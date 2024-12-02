import { Express } from 'express';
import ksuid from 'ksuid';
import { UserPayload } from '../middleware/authenticate';
import { Config } from '../config';
import jwt, { JwtPayload } from 'jsonwebtoken';
import { Enrolment, IEnrolmentRepository } from '../data/enrolmentRepository';
import {
  IStudyRepository,
  PercentageGroupAllocation,
  StudyExperimentalGroup,
} from '../data/studyRepository';
import { InvalidStudyConfigurationError } from '../config/errors';
import {
  listGroupNames,
  rollDiceOverProbabilities,
  validateProbabilities,
} from '../experiment/groups';

export function createEnrolmentController(
  enrolmentRepository: IEnrolmentRepository,
  studyRepository: IStudyRepository,
  app: Express,
) {
  // creates an enrolment and generates a token
  app.post('/v1/enrolment', async (req, res) => {
    if (!req.body.enrolmentKey) {
      return res
        .status(400)
        .send({ error: 'Missing required fields (enrolmentKey)' });
    }

    const study = await studyRepository.getStudyByEnrolmentKey(
      req.body.enrolmentKey,
    );
    if (!study) {
      return res
        .status(404)
        .send({ error: 'Study not found', code: 'not_found' });
    }

    const enrolmentCount = await enrolmentRepository.getEnrolmentCountByStudyId(
      study.id,
    );

    if (study.maxEnrolments == -1) {
      // unlimited enrolments
    } else if (study.maxEnrolments == 0) {
      return res.status(400).send({ error: 'Study is closed', code: 'closed' });
    } else if (enrolmentCount >= study.maxEnrolments) {
      return res.status(400).send({ error: 'Study is full', code: 'full' });
    }

    const experimentalGroups =
      await studyRepository.getExperimentalGroupsByStudyId(study.id);

    if (experimentalGroups.length === 0) {
      return res.status(400).send({
        error: 'Invalid Study Configuration: Study has no experimental groups',
        code: 'invalid_study_configuration',
      });
    }

    try {
      const experimentalGroup = await pickExperimentalGroup(experimentalGroups);

      const participantId = (await ksuid.random()).string;
      const newEnrolment: Pick<
        Enrolment,
        'studyId' | 'participantId' | 'studyExperimentalGroupId'
      > = {
        studyId: study.id,
        participantId: participantId,
        studyExperimentalGroupId: experimentalGroup.id,
      };

      const enrolment = await enrolmentRepository.createEnrolment(newEnrolment);

      const token = generateTokenForEnrolment(enrolment.id);

      res.json({
        participantId,
        studyId: enrolment.studyId,
        configuration: configurationFromExperimentalGroup(experimentalGroup),
        token,
      });
    } catch (e) {
      console.error(e);

      if (e instanceof InvalidStudyConfigurationError) {
        return res
          .status(400)
          .send({ error: e.message, code: 'invalid_study_configuration' });
      }

      return res.status(500).send({ error: 'Internal server error' });
    }
  });

  app.post('/v1/enrolment/:participantId', async (req, res) => {
    const enrolment = await enrolmentRepository.getEnrolmentByParticipantId(
      req.params.participantId,
    );
    if (!enrolment) {
      return res.status(404).send({ error: 'Enrolment not found' });
    }

    const experimentalGroup = await studyRepository.getExperimentalGroupById(
      enrolment.studyExperimentalGroupId,
    );

    if (!experimentalGroup) {
      return res.status(404).send({ error: 'Experimental Group not found' });
    }

    const token = generateTokenForEnrolment(enrolment.id);

    res.json({
      participantId: enrolment.participantId,
      studyId: enrolment.studyId,
      configuration: configurationFromExperimentalGroup(experimentalGroup),
      token,
    });
  });

  const configurationFromExperimentalGroup = (
    group: StudyExperimentalGroup,
  ) => ({
    interactionWidgetStrategy: group.interactionWidgetStrategy,
  });

  const pickExperimentalGroup = async (
    experimentalGroups: StudyExperimentalGroup[],
  ): Promise<StudyExperimentalGroup> => {
    if (experimentalGroups.find((g) => g.allocation.type === 'Manual')) {
      return experimentalGroups[0];
    }
    if (experimentalGroups.every((g) => g.allocation.type === 'Percentage')) {
      const probabilities = experimentalGroups.map(
        (g) => (g.allocation as PercentageGroupAllocation).percentage,
      );

      validateProbabilities(probabilities);

      const index = await rollDiceOverProbabilities(probabilities);

      if (index === -1) {
        throw new InvalidStudyConfigurationError(
          'Forbidden dice roll over percentages',
        );
      }

      return experimentalGroups[index];
    }

    throw new InvalidStudyConfigurationError(
      `Experimental groups are not uniform. Got ${listGroupNames(experimentalGroups)}`,
    );
  };

  console.log('loaded enrolment controller');
}

function generateTokenForEnrolment(enrolmentId: number) {
  const payload: UserPayload = {
    role: 'participant',
    enrolmentId: enrolmentId,
  };

  const token: JwtPayload = {
    iss: Config.app.hostname,
    sub: enrolmentId.toString(),
    iat: Date.now(),
  };

  return jwt.sign(Object.assign({}, payload, token), Config.auth.jwtSecret, {
    expiresIn: '30d',
  });
}
