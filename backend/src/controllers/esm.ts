import {
  ExperienceSamplingQuestionnaire,
  IRepository,
} from '../data/repository';
import { Express, Request, Response } from 'express';
import {
  authenticate,
  RequestUser,
  requireAdmin,
} from '../middleware/authenticate';

export function createESMController(repository: IRepository, app: Express) {
  app.get('/v1/study/:studyId/questionnaire', async (req, res) => {
    const studyId = parseInt(req.params.studyId);
    const questionnaires =
      await repository.getESMQuestionnairesByStudyId(studyId);
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
      const study = await repository.getStudyById(studyId);
      if (!study) {
        return res.status(400).send({ error: 'Study not found' });
      }

      const questionnaire = await repository.createESMQuestionnaire({
        studyId,
        ...req.body,
      });

      res.json(questionnaire);
    },
  );

  app.get(
    '/v1/study/:studyId/questionnaire/:questionnaireId',
    async (req, res) => {
      const studyId = parseInt(req.params.studyId);
      const questionnaireId = parseInt(req.params.questionnaireId);
      const questionnaire =
        await repository.getESMQuestionnaireById(questionnaireId);

      const elements =
        await repository.getESMElementsByQuestionnaireId(questionnaireId);
      const triggers =
        await repository.getESMQuestionnaireTriggersByQuestionnaireId(
          questionnaireId,
        );

      if (!questionnaire) {
        return res.status(404).send({ error: 'Questionnaire not found' });
      }

      res.json({ questionnaire, elements, triggers });
    },
  );

  async function fetchOrFailQuestionnaire(req: Request, res: Response) {
    const studyId = parseInt(req.params.studyId);
    const questionnaireId = parseInt(req.params.questionnaireId);
    const questionnaire =
      await repository.getESMQuestionnaireById(questionnaireId);
    if (!questionnaire) {
      res.status(404).send({ error: 'Questionnaire not found' });
      return null;
    }

    if (questionnaire.studyId !== studyId) {
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

      const updated = await repository.updateESMQuestionnaire(
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

      const element = await repository.createESMElement({
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

      const element = await repository.updateESMElement({
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

      await repository.deleteESMElement(parseInt(req.params.elementId));

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

      const trigger = await repository.createESMQuestionnaireTrigger({
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

      const trigger = await repository.updateESMQuestionnaireTrigger({
        id: parseInt(req.params.triggerId),
        ...req.body,
      });

      res.json(trigger);
    },
  );

  app.delete(
    '/v1/study/:studyId/questionnaire/:questionnaireId/trigger/:triggerId',
    authenticate,
    async (req, res) => {
      const questionnaire = await fetchOrFailQuestionnaire(req, res);
      if (!questionnaire) return;

      await repository.deleteESMQuestionnaireTrigger(
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
      if (!questionnaire) return res.status(404).send();

      const answer = await repository.createESMAnswer({
        questionnaireId: questionnaire.id,
        enrolmentId: (req.user! as RequestUser).enrolmentId,
        answers: JSON.stringify(req.body.answers), // fixme: this could be vulnerable
      });

      res.json(answer);
    },
  );

  console.log('loaded esm controller');
}
