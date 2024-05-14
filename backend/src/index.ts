import express from 'express';
import { usePool } from './config/database';
import { Config } from './config';
import { createStudyController } from './controllers/study';
import { Repository } from './data/repository';
import { createEnrolmentController } from './controllers/enrolment';

async function main() {
  const app = express();
  app.use(express.json());

  const pool = await usePool();
  const repository = new Repository(pool);
  console.log('connected to database');

  app.get('/', (req, res) => {
    res.send('Social Interaction Sensing!');
  });

  createStudyController(repository, app);
  createEnrolmentController(repository, app);

  app.listen(Config.app.port, () => {
    console.log(
      `Server running on http://${Config.app.hostname}:${Config.app.port}`,
    );
  });

  return app;
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
