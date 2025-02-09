import { Express } from 'express';
import ksuid from 'ksuid';
import { UserPayload } from '../middleware/authenticate';
import { Config } from '../config';
import jwt, { JwtPayload } from 'jsonwebtoken';
import { Enrolment, IEnrolmentRepository } from '../data/enrolmentRepository';
import {
  IStudyRepository,
  Study,
  StudyExperimentalGroup,
} from '../data/studyRepository';
import { InvalidStudyConfigurationError } from '../config/errors';
import { Mutex } from 'async-mutex';

export function createEnrolmentController(
  enrolmentRepository: IEnrolmentRepository,
  studyRepository: IStudyRepository,
  app: Express,
) {
  const groupAssignmentMutex = new Mutex();

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

    const availableExperimentalGroups =
      await studyRepository.getExperimentalGroupsByStudyId(study.id);

    if (availableExperimentalGroups.length === 0) {
      return res.status(400).send({
        error: 'Invalid Study Configuration: Study has no experimental groups',
        code: 'invalid_study_configuration',
      });
    }

    const participantId = (await ksuid.random()).string;

    try {
      // todo: this could make it really slow, but it needs to be an atomic operation
      const [enrolment, experimentalGroup] =
        await groupAssignmentMutex.runExclusive(async () => {
          const experimentalGroup = await pickExperimentalGroup(
            study,
            availableExperimentalGroups,
          );

          const newEnrolment: Pick<
            Enrolment,
            'studyId' | 'participantId' | 'studyExperimentalGroupId'
          > = {
            studyId: study.id,
            participantId: participantId,
            studyExperimentalGroupId: experimentalGroup.id,
          };

          const enrolment =
            await enrolmentRepository.createEnrolment(newEnrolment);

          return [enrolment, experimentalGroup];
        });

      const token = generateTokenForEnrolment(enrolment.id);

      const phases =
        await studyRepository.getExperimentalGroupPhasesByExperimentalGroupId(
          experimentalGroup.id,
        );

      res.json({
        participantId,
        studyId: enrolment.studyId,
        phases,
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

    const phases =
      await studyRepository.getExperimentalGroupPhasesByExperimentalGroupId(
        experimentalGroup.id,
      );

    if (phases.length === 0) {
      return res.status(400).send({
        error: 'Invalid Study Configuration: Experimental Group has no phases',
        code: 'invalid_study_configuration',
      });
    }

    const token = generateTokenForEnrolment(enrolment.id);

    res.json({
      participantId: enrolment.participantId,
      studyId: enrolment.studyId,
      phases,
      token,
    });
  });

  const pickExperimentalGroup = async (
    study: Study,
    experimentalGroups: StudyExperimentalGroup[],
  ): Promise<StudyExperimentalGroup> => {
    const sortedGroups = experimentalGroups.sort((g) => g.allocationOrder);

    if (study.allocationStrategy === 'Sequential') {
      const lastEnrolment = await enrolmentRepository.getLastEnrolmentByStudyId(
        study.id,
      );
      if (!lastEnrolment) {
        return sortedGroups[0];
      }

      const lastGroupIndex = sortedGroups.findIndex(
        (g) => g.id === lastEnrolment.studyExperimentalGroupId,
      );

      if (lastGroupIndex === -1) {
        return sortedGroups[0];
      }

      const nextGroupIndex = (lastGroupIndex + 1) % sortedGroups.length;
      return sortedGroups[nextGroupIndex];
    }

    return sortedGroups[0];
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
