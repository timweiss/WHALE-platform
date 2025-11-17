import { Express } from 'express';
import { authenticate, UserPayload } from '../middleware/authenticate';
import { Config } from '../config';
import jwt, { JwtPayload } from 'jsonwebtoken';
import * as z from 'zod';
import { Enrolment, IEnrolmentRepository } from '../data/enrolmentRepository';
import {
  IStudyRepository,
  Study,
  StudyExperimentalGroup,
} from '../data/studyRepository';
import { InvalidStudyConfigurationError } from '../config/errors';
import { Mutex } from 'async-mutex';
import { Observability } from '../o11y';
import { customAlphabet } from 'nanoid';

const CreateEnrolment = z.object({
  enrolmentKey: z.string(),
});

function generateParticipantId() {
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz';
  return customAlphabet(alphabet, 9)();
}

export function createEnrolmentController(
  enrolmentRepository: IEnrolmentRepository,
  studyRepository: IStudyRepository,
  app: Express,
  observability: Observability,
) {
  const groupAssignmentMutex = new Mutex();

  // creates an enrolment and generates a token
  app.post('/v1/enrolment', async (req, res) => {
    return observability.tracer.startActiveSpan(
      'enrolment:create',
      async (span) => {
        const body = CreateEnrolment.safeParse(req.body);
        if (!body.success) {
          span.end();
          return res
            .status(400)
            .send({ error: 'Missing required fields', fields: body.error });
        }

        span.setAttribute('enrolmentKey', body.data.enrolmentKey);

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

        const participantId = generateParticipantId();

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

  app.get('/v1/enrolment', authenticate, async (req, res) => {
    const enrolmentId = (req.user as UserPayload).enrolmentId;
    if (!enrolmentId) {
      return res.status(403).send({ error: 'Enrolment ID missing in token' });
    }

    const enrolment = await enrolmentRepository.getEnrolmentById(enrolmentId);
    if (!enrolment) {
      return res.status(404).send({ error: 'Enrolment not found' });
    }

    res.json({
      enrolmentId: enrolment.id,
      debugEnabled: enrolment.debugEnabled,
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
