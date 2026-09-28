import { Express } from 'express';
import { authenticate, RequestUser } from '../middleware/authenticate';
import { ISensorReadingRepository } from '../data/sensorReadingRepository';
import { IEnrolmentRepository } from '../data/enrolmentRepository';
import { Observability } from '../o11y';
import { ClientSensorReading } from '../model/sensor-reading';
import { z } from 'zod';
import { Queue } from 'bullmq';
import { SensorReadingJobData } from '../queues/sensorReadingQueue';

const ReadingBatchRequestBody = z.array(ClientSensorReading);

export function createReadingController(
  sensorReadingRepository: ISensorReadingRepository,
  enrolmentRepository: IEnrolmentRepository,
  app: Express,
  observability: Observability,
  sensorReadingQueue?: Queue<SensorReadingJobData>,
) {
  app.post('/v1/reading/batch', authenticate, async (req, res) => {
    const parsed = ReadingBatchRequestBody.safeParse(req.body);
    if (!parsed.success) {
      observability.logger.error('Invalid format for batched readings', {
        validation: parsed.error.message,
      });

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
      if (sensorReadingQueue) {
        const job = await sensorReadingQueue.add(
          'batch-sensor-reading',
          {
            enrolmentId: enrolment.id,
            readings: parsed.data,
          },
          {
            jobId: `enrolment-${enrolment.id}-${Date.now()}`,
          },
        );

        observability.logger.info('Sensor reading batch queued', {
          jobId: job.id,
          enrolmentId: enrolment.id,
          readingCount: parsed.data.length,
        });

        res.status(202).json({ jobId: job.id });
      } else {
        // Fallback to synchronous processing if queue is not available
        observability.logger.warn(
          'Queue not available, falling back to synchronous processing',
        );

        await sensorReadingRepository.createSensorReadingBatched(
          enrolment.id,
          parsed.data,
        );

        res.json({});
      }
    } catch (e) {
      observability.logger.error(`Error creating readings ${e}`, {
        error: JSON.stringify(e),
      });
      res.status(500).send({ error: 'Error creating readings' });
    }
  });

  observability.logger.info('loaded reading controller');
}
