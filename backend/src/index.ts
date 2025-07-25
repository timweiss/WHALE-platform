import express from 'express';
import { usePool } from './config/database';
import { Config } from './config';
import { createStudyController } from './controllers/study';
import { createEnrolmentController } from './controllers/enrolment';
import { createReadingController } from './controllers/reading';
import { Pool } from 'pg';
import { createESMController } from './controllers/esm';
import { initializeRepositories, Repositories } from './data/repositoryHelper';
import { createCompletionController } from './controllers/completion';
import { Logger, Observability, setupO11y } from './o11y';

export function makeExpressApp(
  pool: Pool,
  repositories: Repositories,
  observability: Observability,
) {
  const app = express();
  app.use(express.json());

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
  );
  createESMController(
    repositories.experienceSampling,
    repositories.study,
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

  return app;
}

export async function main() {
  const olly = await setupO11y();

  olly.logger.info('Starting up');

  const pool = usePool(olly);

  const app = makeExpressApp(
    pool,
    initializeRepositories(pool, olly),
    olly,
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
