import { Express } from 'express';
import { authenticate, requireAdmin } from '../middleware/authenticate';
import {
  IStudyRepository,
  StudyExperimentalGroupPhase,
} from '../data/studyRepository';
import { Observability } from '../o11y';
import * as z from 'zod';
import { EntityId } from './validation';

const StudyPath = z.object({ id: EntityId });

export function createStudyController(
  studyRepository: IStudyRepository,
  app: Express,
  observability: Observability,
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
    const path = StudyPath.safeParse(req.params);
    if (!path.success) {
      return res.status(400).send({ error: 'Invalid study ID' });
    }
    const id = path.data.id;

    const study = await studyRepository.getStudyById(id);
    if (!study) {
      return res.status(404).send({ error: 'Study not found' });
    }

    // the id in the path is authoritative, not one in the body
    const updatedStudy = await studyRepository.updateStudy({
      ...req.body,
      id,
    });
    res.json(updatedStudy);
  });

  app.get('/v1/study/:idOrKey', async (req, res) => {
    const { idOrKey } = req.params;

    // digits-only values are study ids, everything else is an enrolment key
    if (!/^\d+$/.test(idOrKey)) {
      const study = await studyRepository.getStudyByEnrolmentKey(idOrKey);
      if (!study) {
        return res.status(404).send({ error: 'Study not found' });
      }
      return res.json(study);
    }

    const id = EntityId.safeParse(idOrKey);
    if (!id.success) {
      return res.status(400).send({ error: 'Invalid study ID' });
    }

    const study = await studyRepository.getStudyById(id.data);
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
      const path = StudyPath.safeParse(req.params);
      if (!path.success) {
        return res.status(400).send({ error: 'Invalid study ID' });
      }
      const id = path.data.id;

      const study = await studyRepository.getStudyById(id);
      if (!study) {
        return res.status(404).send({ error: 'Study not found' });
      }

      const experimentalGroup = await studyRepository.createExperimentalGroup({
        studyId: id,
        internalName: req.body.internalName,
        allocationOrder: req.body.allocationOrder,
      });

      const phases: StudyExperimentalGroupPhase[] = [];

      for (const phase of req.body.phases) {
        if (
          !phase.internalName ||
          phase.fromDay == null ||
          !phase.durationDays
        ) {
          return res
            .status(400)
            .send({ error: 'Missing required fields in phase' });
        }

        const createdPhase = await studyRepository.createExperimentalGroupPhase(
          {
            experimentalGroupId: experimentalGroup.id,
            name: phase.internalName,
            fromDay: parseInt(phase.fromDay),
            durationDays: parseInt(phase.durationDays),
            interactionWidgetStrategy: phase.interactionWidgetStrategy,
          },
        );

        phases.push(createdPhase);
      }

      res.json({
        ...experimentalGroup,
        phases,
      });
    },
  );

  observability.logger.info('loaded study controller');
}
