import { Express } from 'express';
import { authenticate, RequestUser } from '../middleware/authenticate';
import { ISensorReadingRepository } from '../data/sensorReadingRepository';
import { IEnrolmentRepository } from '../data/enrolmentRepository';
import { Observability } from '../o11y';
import { ClientSensorReading } from '../model/sensor-reading';
import { z } from 'zod';
import { Queue } from 'bullmq';
import { randomUUID } from 'node:crypto';
import { SensorReadingJobData } from '../queues/sensorReadingQueue';

// the app uploads at most 200 readings per request
const ReadingBatchRequestBody = z.array(ClientSensorReading).max(1000);

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

    if (sensorReadingQueue) {
      try {
        const job = await sensorReadingQueue.add(
          'batch-sensor-reading',
          {
            enrolmentId: enrolment.id,
            readings: parsed.data,
          },
          {
            // unique per batch: BullMQ silently ignores jobs whose id already
            // exists, which dropped batches arriving in the same millisecond
            jobId: `enrolment-${enrolment.id}-${randomUUID()}`,
          },
        );

        observability.logger.info('Sensor reading batch queued', {
          jobId: job.id,
          enrolmentId: enrolment.id,
          readingCount: parsed.data.length,
        });

        return res.status(202).json({ jobId: job.id });
      } catch (e) {
        // e.g. Redis is out of memory; the app keeps the readings and retries
        observability.logger.error('Error queueing sensor reading batch', {
          enrolmentId: enrolment.id,
          error: e instanceof Error ? e.message : String(e),
        });
        return res
          .status(503)
          .send({ error: 'Readings cannot be accepted right now' });
      }
    }

    // Fallback to synchronous processing if queue is not available
    observability.logger.warn(
      'Queue not available, falling back to synchronous processing',
    );

    try {
      await sensorReadingRepository.createSensorReadingBatched(
        enrolment.id,
        parsed.data,
      );
      res.json({});
    } catch (e) {
      observability.logger.error(`Error creating readings ${e}`, {
        error: JSON.stringify(e),
      });
      res.status(500).send({ error: 'Error creating readings' });
    }
  });

  observability.logger.info('loaded reading controller');
}
