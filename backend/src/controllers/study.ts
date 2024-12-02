import { Express } from 'express';
import { authenticate, requireAdmin } from '../middleware/authenticate';
import { IStudyRepository } from '../data/studyRepository';

export function createStudyController(
  studyRepository: IStudyRepository,
  app: Express,
) {
  app.get('/v1/study', async (req, res) => {
    const studies = await studyRepository.getStudies();
    res.json(studies);
  });

  app.post('/v1/study', authenticate, requireAdmin, async (req, res) => {
    if (!req.body.enrolmentKey || !req.body.name) {
      return res
        .status(400)
        .send({ error: 'Missing required fields (enrolmentKey or name)' });
    }

    const existing = await studyRepository.getStudyByEnrolmentKey(
      req.body.enrolmentKey,
    );
    if (existing) {
      return res
        .status(400)
        .send({ error: 'Study with enrolment key already exists' });
    }

    const study = await studyRepository.createStudy(req.body);
    res.json(study);
  });

  app.put('/v1/study/:id', authenticate, requireAdmin, async (req, res) => {
    const id = parseInt(req.params.id);
    if (isNaN(id)) {
      return res.status(400).send({ error: 'Invalid study ID' });
    }

    const study = await studyRepository.getStudyById(id);
    if (!study) {
      return res.status(404).send({ error: 'Study not found' });
    }

    const updatedStudy = await studyRepository.updateStudy(req.body);
    res.json(updatedStudy);
  });

  app.get('/v1/study/:id', async (req, res) => {
    const id = parseInt(req.params.id);
    if (isNaN(id)) {
      return res.status(400).send({ error: 'Invalid study ID' });
    }

    const study = await studyRepository.getStudyById(id);
    if (!study) {
      return res.status(404).send({ error: 'Study not found' });
    }
    res.json(study);
  });

  app.post(
    '/v1/study/:id/group',
    authenticate,
    requireAdmin,
    async (req, res) => {
      const id = parseInt(req.params.id);
      if (isNaN(id)) {
        return res.status(400).send({ error: 'Invalid study ID' });
      }

      const study = await studyRepository.getStudyById(id);
      if (!study) {
        return res.status(404).send({ error: 'Study not found' });
      }

      const configuration = await studyRepository.createExperimentalGroup({
        studyId: id,
        allocation: req.body.allocation,
        internalName: req.body.internalName,
        interactionWidgetStrategy: req.body.interactionWidgetStrategy,
      });
      res.json(configuration);
    },
  );

  console.log('loaded study controller');
}
