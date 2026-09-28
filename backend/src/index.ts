import express from 'express';
import { usePool } from './config/database';
import { Config } from './config';
import { createStudyController } from './controllers/study';
import { createEnrolmentController } from './controllers/enrolment';
import { createReadingController } from './controllers/reading';
import { Pool } from 'pg';
import { createESMConfigController } from './controllers/esmConfig';
import { createESMAnswerController } from './controllers/esmResponse';
import { initializeRepositories, Repositories } from './data/repositoryHelper';
import { createCompletionController } from './controllers/completion';
import { Observability, setupO11y } from './o11y';
import {
  createSensorReadingQueue,
  SensorReadingJobData,
} from './queues/sensorReadingQueue';
import { Queue } from 'bullmq';
import { createErrorHandler } from './middleware/errorHandler';

export function makeExpressApp(
  pool: Pool,
  repositories: Repositories,
  observability: Observability,
  sensorReadingQueue?: Queue<SensorReadingJobData>,
) {
  const app = express();
  app.set('trust proxy', Config.app.trustProxy);
  // the app splits sensor uploads to stay below nginx's default 1 MB limit
  // and shrinks its chunks when it receives a 413
  app.use(express.json({ limit: '2mb' }));

  app.get('/', (req, res) => {
    res.send('Social Interaction Sensing!');
  });

  createStudyController(repositories.study, app, observability);
  createEnrolmentController(
    repositories.enrolment,
    repositories.study,
    app,
    observability,
  );
  createReadingController(
    repositories.sensorReading,
    repositories.enrolment,
    app,
    observability,
    sensorReadingQueue,
  );
  createESMConfigController(
    repositories.esmConfig,
    repositories.study,
    app,
    observability,
  );
  createESMAnswerController(
    repositories.esmAnswer,
    repositories.esmConfig,
    repositories.enrolment,
    app,
    observability,
  );
  createCompletionController(
    repositories.completion,
    repositories.study,
    repositories.enrolment,
    app,
    observability,
  );

  app.use(createErrorHandler(observability));

  return app;
}

export async function main() {
  const olly = await setupO11y();

  olly.logger.info('Starting up');

  const pool = usePool(olly);

  // Initialize the sensor reading queue
  let sensorReadingQueue;
  try {
    sensorReadingQueue = createSensorReadingQueue();
    olly.logger.info('Sensor reading queue initialized');
  } catch (error) {
    olly.logger.error('Failed to initialize sensor reading queue', {
      error: error instanceof Error ? error.message : String(error),
    });
    olly.logger.warn(
      'API will continue without queue, using synchronous processing',
    );
  }

  const app = makeExpressApp(
    pool,
    initializeRepositories(pool, olly),
    olly,
    sensorReadingQueue,
  );

  const server = app.listen(Config.app.port, () => {
    olly.logger.info(`Server listening on port ${Config.app.port}`);
  });

  // close the pool when app shuts down
  process.on('SIGTERM', async () => {
    await pool.end();
    server.close(() => {
      olly.logger.info('HTTP server closed');
    });
    await olly.onShutdown();
    process.exit(0);
  });
}

// only run main app when not in test environment
if (process.env.NODE_ENV !== 'test') {
  main().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
