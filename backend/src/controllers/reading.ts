import { IRepository, SensorReading } from '../data/repository';
import { Express } from 'express';
import { authenticate, RequestUser } from '../middleware/authenticate';
import { upload } from '../middleware/upload';

export function createReadingController(repository: IRepository, app: Express) {
  app.post('/v1/reading', authenticate, async (req, res) => {
    if (!req.body.sensorType || !req.body.data) {
      return res
        .status(400)
        .send({ error: 'Missing required fields (sensorType or data)' });
    }

    const enrolment = await repository.getEnrolmentById(
      (req.user as RequestUser).enrolmentId,
    );
    if (!enrolment) {
      return res.status(403).send({ error: 'Enrolment not found' });
    }

    const reading = await repository.createSensorReading(enrolment.id, {
      sensorType: req.body.sensorType,
      data: req.body.data,
    });

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

    const enrolment = await repository.getEnrolmentById(
      (req.user as RequestUser).enrolmentId,
    );
    if (!enrolment) {
      return res.status(403).send({ error: 'Enrolment not found' });
    }

    const readings = await repository.createSensorReadingBatched(
      enrolment.id,
      req.body,
    );

    res.json(readings);
  });

  app.post(
    '/v1/reading/:readingId/file',
    authenticate,
    upload.single('file'),
    async (req, res) => {
      if (!req.file) {
        return res.status(400).send({ error: 'No file uploaded' });
      }
      const uploaded = await repository.createFile(
        parseInt(req.body.readingId),
        {
          filename: req.file.filename,
          path: req.file.path,
        },
      );

      res.json(uploaded);
    },
  );

  console.log('loaded reading controller');
}
