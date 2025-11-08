import { Queue, Worker, Job } from 'bullmq';
import { Config } from '../config';
import { Observability } from '../o11y.js';
import { ISensorReadingRepository } from '../data/sensorReadingRepository.js';

export const SENSOR_READING_QUEUE_NAME = 'sensor-reading-ingestion';

export interface SensorReadingJobData {
  enrolmentId: number;
  readings: Array<{
    sensorType: string;
    data: string;
    timestamp: number;
    localId: string;
  }>;
}

export function createSensorReadingQueue(): Queue<SensorReadingJobData> {
  return new Queue<SensorReadingJobData>(SENSOR_READING_QUEUE_NAME, {
    connection: {
      host: Config.redis.host,
      port: Config.redis.port,
      password: Config.redis.password,
      maxRetriesPerRequest: null, // Required for BullMQ
    },
    defaultJobOptions: {
      attempts: 3,
      backoff: {
        type: 'exponential',
        delay: 10000,
      },
      removeOnComplete: true,
      removeOnFail: {
        age: 24 * 3600, // Keep failed jobs for one days
      },
    },
  });
}

export function createSensorReadingWorker(
  sensorReadingRepository: ISensorReadingRepository,
  observability: Observability,
): Worker<SensorReadingJobData> {
  const worker = new Worker<SensorReadingJobData>(
    SENSOR_READING_QUEUE_NAME,
    async (job: Job<SensorReadingJobData>) => {
      const { enrolmentId, readings } = job.data;

      observability.logger.info(
        `Processing sensor reading batch for enrolment ${enrolmentId}`,
        {
          jobId: job.id,
          enrolmentId,
          readingCount: readings.length,
        },
      );

      try {
        const startTime = Date.now();

        await sensorReadingRepository.createSensorReadingBulk(
          enrolmentId,
          readings,
        );

        const duration = Date.now() - startTime;

        observability.logger.info(
          `Successfully processed sensor reading batch for enrolment ${enrolmentId}`,
          {
            jobId: job.id,
            enrolmentId,
            readingCount: readings.length,
            durationMs: duration,
          },
        );

        return {
          enrolmentId,
          insertedCount: readings.length,
          durationMs: duration,
        };
      } catch (error) {
        observability.logger.error(
          `Error processing sensor reading batch for enrolment ${enrolmentId}`,
          {
            jobId: job.id,
            enrolmentId,
            error: error instanceof Error ? error.message : String(error),
          },
        );

        // Re-throw to let BullMQ handle retries
        throw error;
      }
    },
    {
      connection: {
        host: Config.redis.host,
        port: Config.redis.port,
        password: Config.redis.password,
        maxRetriesPerRequest: null,
      },
      concurrency: Config.queue.concurrency, // Process multiple jobs in parallel
      limiter: {
        max: Config.queue.maxJobsPerSecond, // Rate limiting
        duration: 1000,
      },
    },
  );

  // Worker event listeners
  worker.on('completed', (job: Job) => {
    observability.logger.debug(`Job ${job.id} completed`, {
      jobId: job.id,
      returnValue: job.returnvalue,
    });
  });

  worker.on('failed', (job: Job | undefined, error: Error) => {
    observability.logger.error(
      `Job ${job?.id} failed after all retry attempts`,
      {
        jobId: job?.id,
        error: error.message,
        attemptsMade: job?.attemptsMade,
      },
    );
  });

  worker.on('error', (error: Error) => {
    observability.logger.error('Worker error', {
      error: error.message,
    });
  });

  return worker;
}
