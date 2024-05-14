import { Express } from 'express';
import { IRepository } from '../data/repository';
import { authenticate, requireAdmin } from '../middleware/authenticate';

export function createStudyController(repository: IRepository, app: Express) {
  app.get('/v1/study', async (req, res) => {
    const studies = await repository.getStudies();
    res.json(studies);
  });

  app.post('/v1/study', authenticate, requireAdmin, async (req, res) => {
    const existing = await repository.getStudyByEnrolmentKey(
      req.body.enrolmentKey,
    );
    if (existing) {
      return res
        .status(400)
        .send({ error: 'Study with enrolment key already exists' });
    }

    const study = await repository.createStudy(req.body);
    res.json(study);
  });

  app.get('/v1/study/:id', async (req, res) => {
    const study = await repository.getStudyById(parseInt(req.params.id));
    if (!study) {
      return res.status(404).send({ error: 'Study not found' });
    }
    res.json(study);
  });

  console.log('loaded study controller');
}
