import { Express } from 'express';
import { authenticate, RequestUser } from '../middleware/authenticate';
import { upload } from '../middleware/upload';
import { ISensorReadingRepository } from '../data/sensorReadingRepository';
import { IEnrolmentRepository } from '../data/enrolmentRepository';
import { HistogramSensorUpload, Observability, withDuration } from '../o11y';
import { ClientSensorReading } from '../model/sensor-reading';
import { z } from 'zod';

const ReadingBatchRequestBody = z.array(ClientSensorReading);

export function createReadingController(
  sensorReadingRepository: ISensorReadingRepository,
  enrolmentRepository: IEnrolmentRepository,
  app: Express,
  observability: Observability,
) {
  app.post('/v1/reading', authenticate, async (req, res) => {
    const parsed = ClientSensorReading.safeParse(req.body);
    if (!parsed.success) {
      return res
        .status(400)
        .send({ error: 'Invalid request', details: parsed.error });
    }

    const enrolment = await enrolmentRepository.getEnrolmentById(
      (req.user as RequestUser).enrolmentId,
    );
    if (!enrolment) {
      return res.status(403).send({ error: 'Enrolment not found' });
    }

    const reading = await sensorReadingRepository.createSensorReading(
      enrolment.id,
      parsed.data,
    );

    res.json(reading);
  });

  app.post('/v1/reading/batch', authenticate, async (req, res) => {
    const parsed = ReadingBatchRequestBody.safeParse(req.body);
    if (!parsed.success) {
      return res
        .status(400)
        .send({ error: 'Invalid request', details: parsed.error });
    }

    const enrolment = await enrolmentRepository.getEnrolmentById(
      (req.user as RequestUser).enrolmentId,
    );
    if (!enrolment) {
      return res.status(403).send({ error: 'Enrolment not found' });
    }

    try {
      await withDuration(
        async () => {
          return await sensorReadingRepository.createSensorReadingBatched(
            enrolment.id,
            parsed.data,
          );
        },
        (duration) => HistogramSensorUpload(duration),
      );
      res.json({});
    } catch (e) {
      res.status(500).send({ error: 'Error creating readings' });
    }
  });

  app.post(
    '/v1/reading/:readingId/file',
    authenticate,
    upload.single('file'),
    async (req, res) => {
      if (!req.file) {
        return res.status(400).send({ error: 'No file uploaded' });
      }
      const uploaded = await sensorReadingRepository.createFile(
        parseInt(req.params.readingId),
        {
          filename: req.file.filename,
          path: req.file.path,
        },
      );

      res.json(uploaded);
    },
  );

  observability.logger.info('loaded reading controller');
}
