import express from 'express';
import { usePool } from './config/database';
import { Config } from './config';
import { createStudyController } from './controllers/study';
import { IRepository, Repository } from './data/repository';
import { createEnrolmentController } from './controllers/enrolment';
import { createReadingController } from './controllers/reading';
import { Pool } from 'pg';

export function makeExpressApp(pool: Pool, repository: IRepository) {
  const app = express();
  app.use(express.json());

  app.get('/', (req, res) => {
    res.send('Social Interaction Sensing!');
  });

  createStudyController(repository, app);
  createEnrolmentController(repository, app);
  createReadingController(repository, app);

  return app;
}

export async function main() {
  const pool = usePool();
  const repository = new Repository(pool);
  const app = makeExpressApp(pool, repository);

  const server = app.listen(Config.app.port, () => {
    console.log(`Server listening on port ${Config.app.port}`);
  });

  // close the pool when app shuts down
  process.on('SIGTERM', () => {
    pool.end();
    server.close(() => {
      console.log('HTTP server closed');
    });
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
