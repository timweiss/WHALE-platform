import { Express } from 'express';
import { authenticate, requireAdmin } from '../middleware/authenticate';
import {
  InteractionWidgetStrategy,
  IStudyRepository,
  StudyExperimentalGroupPhase,
} from '../data/studyRepository';
import { Observability } from '../o11y';
import * as z from 'zod';
import { EntityId } from './validation';
import { createHourlyRateLimit } from '../middleware/rateLimit';
import { Config } from '../config';

const StudyPath = z.object({ id: EntityId });

// app-android/docs/content-extraction.md; absent or null: no content extraction
const ContentExtractionBody = z
  .object({
    model: z.object({
      sha256: z.string().regex(/^[0-9a-f]{64}$/),
      sizeBytes: z.int().positive(),
    }),
    packages: z
      .array(
        z.object({
          packageName: z.string().min(1),
          hint: z.string().nullable(),
        }),
      )
      .min(1),
    sampleRate: z.number().gt(0).lte(1).default(1),
  })
  .nullable()
  .optional();

const CreateExperimentalGroupBody = z.object({
  internalName: z.string(),
  allocationOrder: z.int(),
  phases: z.array(
    z.object({
      internalName: z.string(),
      fromDay: z.int(),
      durationDays: z.int().positive(),
      interactionWidgetStrategy: z.enum(InteractionWidgetStrategy),
      sensorSettings: z.object({
        uiTreeTextSettings: z
          .object({
            packages: z.array(z.string()),
          })
          .nullable(),
        screenshotSettings: z
          .object({
            periodEnds: z.array(z.iso.time()).min(1),
            packageSettings: z.array(
              z.object({
                packageName: z.string(),
                sample: z.int().positive(),
              }),
            ),
          })
          .nullable(),
      }),
    }),
  ),
});

export function createStudyController(
  studyRepository: IStudyRepository,
  app: Express,
  observability: Observability,
) {
  app.post('/v1/study', authenticate, requireAdmin, async (req, res) => {
    if (!req.body.enrolmentKey || !req.body.name) {
      return res
        .status(400)
        .send({ error: 'Missing required fields (enrolmentKey or name)' });
    }

    const contentExtraction = ContentExtractionBody.safeParse(
      req.body.contentExtraction,
    );
    if (!contentExtraction.success) {
      return res
        .status(400)
        .send({ error: 'Invalid content extraction configuration' });
    }

    const existing = await studyRepository.getStudyByEnrolmentKey(
      req.body.enrolmentKey,
    );
    if (existing) {
      return res
        .status(400)
        .send({ error: 'Study with enrolment key already exists' });
    }

    const study = await studyRepository.createStudy({
      ...req.body,
      contentExtraction: contentExtraction.data ?? null,
    });
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

    const contentExtraction = ContentExtractionBody.safeParse(
      req.body.contentExtraction,
    );
    if (!contentExtraction.success) {
      return res
        .status(400)
        .send({ error: 'Invalid content extraction configuration' });
    }

    // the id in the path is authoritative, not one in the body. Like every
    // field, a missing contentExtraction replaces the stored one (with null).
    const updatedStudy = await studyRepository.updateStudy({
      ...req.body,
      contentExtraction: contentExtraction.data ?? null,
      id,
    });
    res.json(updatedStudy);
  });

  // digits-only values are study ids, everything else is an enrolment key
  const isStudyId = (idOrKey: string) => /^\d+$/.test(idOrKey);

  // only lookups by key are limited, they could be used to guess keys
  const studyKeyLookupRateLimit = createHourlyRateLimit(
    Config.rateLimit.studyKeyLookupsPerHour,
    (req) => isStudyId(req.params.idOrKey as string),
  );

  app.get('/v1/study/:idOrKey', studyKeyLookupRateLimit, async (req, res) => {
    const idOrKey = req.params.idOrKey as string;

    if (!isStudyId(idOrKey)) {
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

    // ids can be enumerated, so the key is only returned to callers that
    // already know it (lookup by key). The app expects a string here.
    res.json({ ...study, enrolmentKey: '' });
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

      const body = CreateExperimentalGroupBody.safeParse(req.body);
      if (!body.success) {
        return res
          .status(400)
          .send({ error: 'Invalid experimental group configuration ' });
      }

      const experimentalGroup = await studyRepository.createExperimentalGroup({
        studyId: id,
        internalName: body.data.internalName,
        allocationOrder: body.data.allocationOrder,
      });

      const phases: StudyExperimentalGroupPhase[] = [];

      for (const phase of body.data.phases) {
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
            fromDay: phase.fromDay,
            durationDays: phase.durationDays,
            interactionWidgetStrategy: phase.interactionWidgetStrategy,
            sensorSettings: phase.sensorSettings,
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
