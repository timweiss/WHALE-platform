import { Enrolment, IRepository } from '../data/repository';
import { Express } from 'express';
import ksuid from 'ksuid';

export function createEnrolmentController(
  repository: IRepository,
  app: Express,
) {
  // creates an enrolment and generates a token
  app.post('/v1/enrolment', async (req, res) => {
    const study = await repository.getStudyByEnrolmentKey(
      req.body.enrolmentKey,
    );
    if (!study) {
      return res.status(404).send({ error: 'Study not found' });
    }

    const participantId = (await ksuid.random()).string;
    let enrolment: Pick<Enrolment, 'studyId' | 'participantId'> = {
      studyId: study.id,
      participantId: participantId,
    };

    enrolment = await repository.createEnrolment(enrolment);

    // todo: generate token

    res.json(enrolment);
  });
}
