import { Express } from 'express';
import { authenticate, RequestUser } from '../middleware/authenticate';
import { upload } from '../middleware/upload';
import {
  ISensorReadingRepository,
  SensorReading,
} from '../data/sensorReadingRepository';
import { IEnrolmentRepository } from '../data/enrolmentRepository';
import { HistogramSensorUpload, Logger, Observability, withDuration } from '../o11y';

export function createReadingController(
  sensorReadingRepository: ISensorReadingRepository,
  enrolmentRepository: IEnrolmentRepository,
  app: Express,
  observability: Observability,
) {
  app.post('/v1/reading', authenticate, async (req, res) => {
    if (!req.body.sensorType || !req.body.data) {
      return res
        .status(400)
        .send({ error: 'Missing required fields (sensorType or data)' });
    }

    const enrolment = await enrolmentRepository.getEnrolmentById(
      (req.user as RequestUser).enrolmentId,
    );
    if (!enrolment) {
      return res.status(403).send({ error: 'Enrolment not found' });
    }

    const reading = await sensorReadingRepository.createSensorReading(
      enrolment.id,
      {
        sensorType: req.body.sensorType,
        data: req.body.data,
        timestamp: req.body.timestamp,
      },
    );

    res.json(reading);
  });

  app.post('/v1/reading/batch', authenticate, async (req, res) => {
    if (
      req.body.filter((r: SensorReading) => !r.sensorType || !r.data).length > 0
    ) {
      return res
        .status(400)
        .send({ error: 'Missing required fields (sensorType or data)' });
    }

    const enrolment = await enrolmentRepository.getEnrolmentById(
      (req.user as RequestUser).enrolmentId,
    );
    if (!enrolment) {
      return res.status(403).send({ error: 'Enrolment not found' });
    }

    try {
      const readings = withDuration(
        async () => {
          return await sensorReadingRepository.createSensorReadingBatched(
            enrolment.id,
            req.body,
          );
        },
        (duration) => HistogramSensorUpload(duration),
      );
      res.json(readings);
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
