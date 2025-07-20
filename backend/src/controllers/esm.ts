import { Express, Request, Response } from 'express';
import {
  authenticate,
  RequestUser,
  requireAdmin,
} from '../middleware/authenticate';
import {
  ExperienceSamplingQuestionnaire,
  IExperienceSamplingRepository,
} from '../data/experienceSamplingRepository';
import { IStudyRepository } from '../data/studyRepository';
import { Observability } from '../o11y';

import * as z from 'zod';

const QuestionnaireAnswerBody = z.array(
  z.object({
    elementId: z.number(),
    elementName: z.string(),
    value: z.string(),
  }),
);

const QuestionnairePath = z.object({
  studyId: z.coerce.number(),
  questionnaireId: z.coerce.number(),
  elementId: z.coerce.number().optional(),
});

export function createESMController(
  esmRepository: IExperienceSamplingRepository,
  studyRepository: IStudyRepository,
  app: Express,
  observability: Observability,
) {
  app.get('/v1/study/:studyId/questionnaire', async (req, res) => {
    const studyId = parseInt(req.params.studyId);
    const questionnaires =
      await esmRepository.getESMQuestionnairesByStudyId(studyId);
    if (!questionnaires) {
      return res.status(404).send({ error: 'Study not found' });
    }

    res.json(questionnaires);
  });

  app.post(
    '/v1/study/:studyId/questionnaire',
    authenticate,
    requireAdmin,
    async (req, res) => {
      const studyId = parseInt(req.params.studyId);
      const study = await studyRepository.getStudyById(studyId);
      if (!study) {
        return res.status(400).send({ error: 'Study not found' });
      }

      const questionnaire = await esmRepository.createESMQuestionnaire({
        studyId,
        ...req.body,
      });

      res.json(questionnaire);
    },
  );

  app.get(
    '/v1/study/:studyId/questionnaire/:questionnaireId',
    async (req, res) => {
      const questionnaire = await fetchOrFailQuestionnaire(req, res);
      if (!questionnaire) return;

      const elements = await esmRepository.getESMElementsByQuestionnaireId(
        questionnaire.id,
      );

      const triggers =
        await esmRepository.getESMQuestionnaireTriggersByQuestionnaireId(
          questionnaire.id,
        );

      res.json({ questionnaire, elements, triggers });
    },
  );

  async function fetchOrFailQuestionnaire(req: Request, res: Response) {
    const path = QuestionnairePath.safeParse(req.params);
    if (!path.success) {
      res.status(400).send({ error: 'Invalid request', errors: path.error });
      return null;
    }

    const questionnaire = await esmRepository.getESMQuestionnaireById(
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

  app.put(
    '/v1/study/:studyId/questionnaire/:questionnaireId',
    authenticate,
    requireAdmin,
    async (req, res) => {
      const questionnaire = await fetchOrFailQuestionnaire(req, res);
      if (!questionnaire) return;

      const updated = await esmRepository.updateESMQuestionnaire(
        req.body as ExperienceSamplingQuestionnaire,
      );
      res.json(updated);
    },
  );

  app.post(
    '/v1/study/:studyId/questionnaire/:questionnaireId/element',
    authenticate,
    requireAdmin,
    async (req, res) => {
      const questionnaire = await fetchOrFailQuestionnaire(req, res);
      if (!questionnaire) return;

      const element = await esmRepository.createESMElement({
        questionnaireId: questionnaire.id,
        ...req.body,
      });

      res.json(element);
    },
  );

  app.put(
    '/v1/study/:studyId/questionnaire/:questionnaireId/element/:elementId',
    authenticate,
    requireAdmin,
    async (req, res) => {
      const questionnaire = await fetchOrFailQuestionnaire(req, res);
      if (!questionnaire) return;

      const element = await esmRepository.updateESMElement({
        id: parseInt(req.params.elementId),
        ...req.body,
      });

      res.json(element);
    },
  );

  app.delete(
    '/v1/study/:studyId/questionnaire/:questionnaireId/element/:elementId',
    authenticate,
    requireAdmin,
    async (req, res) => {
      const questionnaire = await fetchOrFailQuestionnaire(req, res);
      if (!questionnaire) return;

      await esmRepository.deleteESMElement(parseInt(req.params.elementId));

      res.status(204).send();
    },
  );

  app.post(
    '/v1/study/:studyId/questionnaire/:questionnaireId/trigger',
    authenticate,
    requireAdmin,
    async (req, res) => {
      const questionnaire = await fetchOrFailQuestionnaire(req, res);
      if (!questionnaire) return;

      const trigger = await esmRepository.createESMQuestionnaireTrigger({
        questionnaireId: questionnaire.id,
        ...req.body,
      });

      res.json(trigger);
    },
  );

  app.put(
    '/v1/study/:studyId/questionnaire/:questionnaireId/trigger/:triggerId',
    authenticate,
    requireAdmin,
    async (req, res) => {
      const questionnaire = await fetchOrFailQuestionnaire(req, res);
      if (!questionnaire) return;

      const trigger = await esmRepository.updateESMQuestionnaireTrigger({
        id: parseInt(req.params.triggerId),
        ...req.body,
      });

      res.json(trigger);
    },
  );

  app.delete(
    '/v1/study/:studyId/questionnaire/:questionnaireId/trigger/:triggerId',
    authenticate,
    requireAdmin,
    async (req, res) => {
      const questionnaire = await fetchOrFailQuestionnaire(req, res);
      if (!questionnaire) return;

      await esmRepository.deleteESMQuestionnaireTrigger(
        parseInt(req.params.triggerId),
      );

      res.status(204).send();
    },
  );

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

      const answer = await esmRepository.createESMAnswer({
        questionnaireId: questionnaire.id,
        enrolmentId: (req.user! as RequestUser).enrolmentId,
        answers: JSON.stringify(answerSchema.data),
      });

      res.json(answer);
    },
  );

  observability.logger.info('loaded esm controller');
}
