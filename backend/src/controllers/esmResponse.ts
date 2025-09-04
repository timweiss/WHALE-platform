import { Express, Request, Response } from 'express';
import * as z from 'zod';
import { authenticate, RequestUser } from '../middleware/authenticate';
import {
  ExperienceSamplingAnswerStatus,
  IESMAnswerRepository,
} from '../data/esmAnswerRepository';
import {
  ExperienceSamplingQuestionnaire,
  IESMConfigRepository,
} from '../data/esmConfigRepository';
import { Observability } from '../o11y';
import {
  NotificationTrigger,
  NotificationTriggerValidation,
} from '../model/notification-trigger';

const QuestionnaireAnswerBody = z.object({
  pendingQuestionnaireId: z.uuid(),
  status: z.enum(ExperienceSamplingAnswerStatus),
  lastOpenedPage: z.number(),
  createdTimestamp: z.number(),
  lastUpdatedTimestamp: z.number(),
  finishedTimestamp: z.number().nullable(),
  notificationTrigger: NotificationTriggerValidation,
  answers: z.array(
    z.object({
      elementId: z.number(),
      elementName: z.string(),
      value: z.string(),
    }),
  ),
});

const QuestionnairePath = z.object({
  studyId: z.coerce.number(),
  questionnaireId: z.coerce.number(),
});

enum EntityUpdateStatus {
  Created = 'created',
  Updated = 'updated',
  Skipped = 'skipped',
}

type EntityUpdate<T> = { status: EntityUpdateStatus; id: T };

export function createESMAnswerController(
  esmResponseRepository: IESMAnswerRepository,
  esmConfigRepository: IESMConfigRepository,
  app: Express,
  observability: Observability,
) {
  async function fetchOrFailQuestionnaire(req: Request, res: Response) {
    const path = QuestionnairePath.safeParse(req.params);
    if (!path.success) {
      res.status(400).send({ error: 'Invalid request', errors: path.error });
      return null;
    }

    const questionnaire = await esmConfigRepository.getESMQuestionnaireById(
      path.data.questionnaireId,
    );

    if (!questionnaire) {
      res.status(404).send({ error: 'Questionnaire not found' });
      return null;
    }

    if (questionnaire.studyId !== path.data.studyId) {
      res.status(403).send({ error: 'Forbidden' });
      return null;
    }

    return questionnaire;
  }

  async function saveOrUpdateAnswer(
    questionnaire: ExperienceSamplingQuestionnaire,
    user: RequestUser,
    answer: z.infer<typeof QuestionnaireAnswerBody>,
  ): Promise<EntityUpdate<number>> {
    const existingAnswer =
      await esmResponseRepository.getESMAnswerForPendingQuestionnaireId(
        answer.pendingQuestionnaireId,
      );
    if (
      existingAnswer &&
      existingAnswer.status == ExperienceSamplingAnswerStatus.Completed
    ) {
      return {
        status: EntityUpdateStatus.Skipped,
        id: existingAnswer.id,
      };
    }

    let notificationTriggerId: string | null = null;
    if (answer.notificationTrigger) {
      notificationTriggerId = await saveOrUpdateNotificationTrigger(
        (user as RequestUser).enrolmentId,
        answer.notificationTrigger,
      ).then((result) => result.id);
    }

    const createdAnswer = await esmResponseRepository.createESMAnswer({
      questionnaireId: questionnaire.id,
      enrolmentId: (user! as RequestUser).enrolmentId,
      answers: JSON.stringify(answer.answers),
      pendingQuestionnaireId: answer.pendingQuestionnaireId,
      status: answer.status,
      lastOpenedPage: answer.lastOpenedPage,
      createdTimestamp: answer.createdTimestamp,
      lastUpdatedTimestamp: answer.lastUpdatedTimestamp,
      finishedTimestamp: answer.finishedTimestamp,
      notificationTriggerId: notificationTriggerId,
    });

    return { status: EntityUpdateStatus.Created, id: createdAnswer.id };
  }

  async function saveOrUpdateNotificationTrigger(
    enrolmentId: number,
    notificationTrigger: NotificationTrigger,
  ): Promise<EntityUpdate<string>> {
    const existing =
      await esmResponseRepository.getNotificationTriggerByLocalId(
        enrolmentId,
        notificationTrigger.uid,
      );
    if (existing && existing.updatedAt >= notificationTrigger.updatedAt) {
      return { status: EntityUpdateStatus.Skipped, id: existing.uid };
    }

    if (existing) {
      const updated = await esmResponseRepository.updateNotificationTrigger(
        enrolmentId,
        notificationTrigger,
      );
      if (updated) {
        return { status: EntityUpdateStatus.Updated, id: updated.uid };
      }
    } else {
      const created = await esmResponseRepository.createNotificationTrigger(
        enrolmentId,
        notificationTrigger,
      );
      if (created) {
        return { status: EntityUpdateStatus.Created, id: created.uid };
      }
    }

    throw new Error('Failed to create or update notification trigger');
  }

  app.post(
    '/v1/study/:studyId/questionnaire/:questionnaireId/answer',
    authenticate,
    async (req, res) => {
      const questionnaire = await fetchOrFailQuestionnaire(req, res);
      if (!questionnaire) return;

      const answerSchema = QuestionnaireAnswerBody.safeParse(req.body);
      if (!answerSchema.success) {
        return res.status(400).send({
          error: 'Invalid answer format',
          details: answerSchema.error,
        });
      }

      const result = await saveOrUpdateAnswer(
        questionnaire,
        req.user as RequestUser,
        answerSchema.data,
      );

      res.json(result);
    },
  );

  observability.logger.info('loaded esm response controller');
}
