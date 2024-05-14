import { Express } from 'express';
import { IRepository } from '../data/repository';

export function createStudyController(repository: IRepository, app: Express) {
  app.get('/study', async (req, res) => {
    res.json({ message: 'hello study!' });
  });

  console.log('loaded study controller');
}
