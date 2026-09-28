import { Express, Request, Response } from 'express';
import * as z from 'zod';
import { authenticate, requireAdmin } from '../middleware/authenticate';
import {
  ExperienceSamplingQuestionnaire,
  IESMConfigRepository,
} from '../data/esmConfigRepository';
import { IStudyRepository } from '../data/studyRepository';
import { Observability } from '../o11y';
import { EntityId } from './validation';

const StudyPath = z.object({
  studyId: EntityId,
});

const QuestionnairePath = z.object({
  studyId: EntityId,
  questionnaireId: EntityId,
  elementId: EntityId.optional(),
  triggerId: EntityId.optional(),
});

export function createESMConfigController(
  esmConfigRepository: IESMConfigRepository,
  studyRepository: IStudyRepository,
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

    return { questionnaire, path: path.data };
  }

  app.get('/v1/study/:studyId/questionnaire', async (req, res) => {
    const path = StudyPath.safeParse(req.params);
    if (!path.success) {
      return res.status(400).send({ error: 'Invalid request' });
    }

    const questionnaires =
      await esmConfigRepository.getESMQuestionnairesByStudyId(
        path.data.studyId,
      );
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
      const path = StudyPath.safeParse(req.params);
      if (!path.success) {
        return res.status(400).send({ error: 'Invalid request' });
      }
      const studyId = path.data.studyId;

      const study = await studyRepository.getStudyById(studyId);
      if (!study) {
        return res.status(400).send({ error: 'Study not found' });
      }

      const questionnaire = await esmConfigRepository.createESMQuestionnaire({
        studyId,
        ...req.body,
      });

      res.json(questionnaire);
    },
  );

  app.get(
    '/v1/study/:studyId/questionnaire/:questionnaireId',
    async (req, res) => {
      const found = await fetchOrFailQuestionnaire(req, res);
      if (!found) return;
      const { questionnaire } = found;

      const elements =
        await esmConfigRepository.getESMElementsByQuestionnaireId(
          questionnaire.id,
        );

      const triggers =
        await esmConfigRepository.getESMQuestionnaireTriggersByQuestionnaireId(
          questionnaire.id,
        );

      res.json({ questionnaire, elements, triggers });
    },
  );

  app.put(
    '/v1/study/:studyId/questionnaire/:questionnaireId',
    authenticate,
    requireAdmin,
    async (req, res) => {
      const found = await fetchOrFailQuestionnaire(req, res);
      if (!found) return;
      const { questionnaire } = found;

      // the id in the path is authoritative, not one in the body
      const updated = await esmConfigRepository.updateESMQuestionnaire({
        ...(req.body as ExperienceSamplingQuestionnaire),
        id: questionnaire.id,
      });
      res.json(updated);
    },
  );

  app.post(
    '/v1/study/:studyId/questionnaire/:questionnaireId/element',
    authenticate,
    requireAdmin,
    async (req, res) => {
      const found = await fetchOrFailQuestionnaire(req, res);
      if (!found) return;
      const { questionnaire } = found;

      const element = await esmConfigRepository.createESMElement({
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
      const found = await fetchOrFailQuestionnaire(req, res);
      if (!found) return;
      const { path } = found;

      const element = await esmConfigRepository.updateESMElement({
        ...req.body,
        id: path.elementId!,
      });

      res.json(element);
    },
  );

  app.delete(
    '/v1/study/:studyId/questionnaire/:questionnaireId/element/:elementId',
    authenticate,
    requireAdmin,
    async (req, res) => {
      const found = await fetchOrFailQuestionnaire(req, res);
      if (!found) return;
      const { path } = found;

      await esmConfigRepository.deleteESMElement(path.elementId!);

      res.status(204).send();
    },
  );

  app.post(
    '/v1/study/:studyId/questionnaire/:questionnaireId/trigger',
    authenticate,
    requireAdmin,
    async (req, res) => {
      const found = await fetchOrFailQuestionnaire(req, res);
      if (!found) return;
      const { questionnaire } = found;

      const trigger = await esmConfigRepository.createESMQuestionnaireTrigger({
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
      const found = await fetchOrFailQuestionnaire(req, res);
      if (!found) return;
      const { path } = found;

      const trigger = await esmConfigRepository.updateESMQuestionnaireTrigger({
        ...req.body,
        id: path.triggerId!,
      });

      res.json(trigger);
    },
  );

  app.delete(
    '/v1/study/:studyId/questionnaire/:questionnaireId/trigger/:triggerId',
    authenticate,
    requireAdmin,
    async (req, res) => {
      const found = await fetchOrFailQuestionnaire(req, res);
      if (!found) return;
      const { path } = found;

      await esmConfigRepository.deleteESMQuestionnaireTrigger(path.triggerId!);

      res.status(204).send();
    },
  );

  observability.logger.info('loaded esm config controller');
}
