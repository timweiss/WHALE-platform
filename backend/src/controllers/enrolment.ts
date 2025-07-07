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
import { Observability } from '../o11y';

export function createEnrolmentController(
  enrolmentRepository: IEnrolmentRepository,
  studyRepository: IStudyRepository,
  app: Express,
  observability: Observability,
) {
  const groupAssignmentMutex = new Mutex();

  // creates an enrolment and generates a token
  app.post('/v1/enrolment', async (req, res) => {
    return await observability.tracer.startActiveSpan(
      'enrolment:create',
      async (span) => {
        if (!req.body.enrolmentKey) {
          span.end();
          return res
            .status(400)
            .send({ error: 'Missing required fields (enrolmentKey)' });
        }

        span.setAttribute('enrolmentKey', req.body.enrolmentKey);

        const study = await studyRepository.getStudyByEnrolmentKey(
          req.body.enrolmentKey,
        );
        if (!study) {
          span.end();
          return res
            .status(404)
            .send({ error: 'Study not found', code: 'not_found' });
        }

        const enrolmentCount =
          await enrolmentRepository.getEnrolmentCountByStudyId(study.id);

        if (study.maxEnrolments == -1) {
          // unlimited enrolments
        } else if (study.maxEnrolments == 0) {
          observability.logger.info('Study is closed', {
            studyId: study.id,
          });
          span.end();
          return res
            .status(400)
            .send({ error: 'Study is closed', code: 'closed' });
        } else if (enrolmentCount >= study.maxEnrolments) {
          observability.logger.info('Study is full', {
            studyId: study.id,
            enrolmentCount,
            maxEnrolments: study.maxEnrolments,
          });
          span.end();
          return res.status(400).send({ error: 'Study is full', code: 'full' });
        }

        const availableExperimentalGroups =
          await studyRepository.getExperimentalGroupsByStudyId(study.id);

        if (availableExperimentalGroups.length === 0) {
          observability.logger.warn(
            'Invalid study configuration: no experimental groups',
            {
              studyId: study.id,
            },
          );
          span.end();
          return res.status(400).send({
            error:
              'Invalid Study Configuration: Study has no experimental groups',
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

          span.end();
          return res.json({
            participantId,
            studyId: enrolment.studyId,
            phases,
            token,
          });
        } catch (e) {
          if (e instanceof Error) {
            span.recordException(e);
          }

          if (e instanceof InvalidStudyConfigurationError) {
            observability.logger.warn('Invalid study configuration', {
              studyId: study.id,
              availableExperimentalGroups,
            });
            span.end();
            return res
              .status(400)
              .send({ error: e.message, code: 'invalid_study_configuration' });
          }

          span.end();
          return res.status(500).send({ error: 'Internal server error' });
        }
      },
    );
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
      observability.logger.warn('Experimental Group not found for enrolment', {
        enrolmentId: enrolment.id,
        enrolmentExperimentalGroupId: enrolment.studyExperimentalGroupId,
      });

      return res.status(404).send({ error: 'Experimental Group not found' });
    }

    const phases =
      await studyRepository.getExperimentalGroupPhasesByExperimentalGroupId(
        experimentalGroup.id,
      );

    if (phases.length === 0) {
      observability.logger.warn('Experimental Group has no phases', {
        experimentalGroupId: experimentalGroup.id,
      });

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

  observability.logger.info('loaded enrolment controller');
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
