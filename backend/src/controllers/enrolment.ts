import { Enrolment, IRepository } from '../data/repository';
import { Express } from 'express';
import ksuid from 'ksuid';
import { UserPayload } from '../middleware/authenticate';
import { Config } from '../config';
import jwt, { JwtPayload } from 'jsonwebtoken';

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
    const newEnrolment: Pick<Enrolment, 'studyId' | 'participantId'> = {
      studyId: study.id,
      participantId: participantId,
    };

    const enrolment = await repository.createEnrolment(newEnrolment);

    const token = generateTokenForEnrolment(enrolment.id);

    res.json({ participantId, token });
  });

  app.post('/v1/enrolment/:participantId', async (req, res) => {
    const enrolment = await repository.getEnrolmentByParticipantId(
      req.params.participantId,
    );
    if (!enrolment) {
      return res.status(404).send({ error: 'Enrolment not found' });
    }

    const token = generateTokenForEnrolment(enrolment.id);

    res.json({ participantId: enrolment.participantId, token });
  });

  console.log('loaded enrolment controller');
}

function generateTokenForEnrolment(enrolmentId: number) {
  const payload: UserPayload = {
    role: 'participant',
    enrolmentId: enrolmentId,
  };

  const token: JwtPayload = {
    iss: Config.app.hostname,
    sub: enrolmentId.toString(),
    iat: Date.now(),
  };

  return jwt.sign(Object.assign({}, payload, token), Config.auth.jwtSecret, {
    expiresIn: '30d',
  });
}
