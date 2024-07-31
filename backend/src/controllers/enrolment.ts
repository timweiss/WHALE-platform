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
    if (!req.body.enrolmentKey) {
      return res
        .status(400)
        .send({ error: 'Missing required fields (enrolmentKey)' });
    }

    const study = await repository.getStudyByEnrolmentKey(
      req.body.enrolmentKey,
    );
    if (!study) {
      return res
        .status(404)
        .send({ error: 'Study not found', code: 'not_found' });
    }

    const enrolmentCount = await repository.getEnrolmentCountByStudyId(
      study.id,
    );

    if (study.maxEnrolments == -1) {
      // unlimited enrolments
    } else if (study.maxEnrolments == 0) {
      return res.status(400).send({ error: 'Study is closed', code: 'closed' });
    } else if (enrolmentCount >= study.maxEnrolments) {
      return res.status(400).send({ error: 'Study is full', code: 'full' });
    }

    const participantId = (await ksuid.random()).string;
    const newEnrolment: Pick<Enrolment, 'studyId' | 'participantId'> = {
      studyId: study.id,
      participantId: participantId,
    };

    const enrolment = await repository.createEnrolment(newEnrolment);

    const token = generateTokenForEnrolment(enrolment.id);

    res.json({ participantId, studyId: enrolment.studyId, token });
  });

  app.post('/v1/enrolment/:participantId', async (req, res) => {
    const enrolment = await repository.getEnrolmentByParticipantId(
      req.params.participantId,
    );
    if (!enrolment) {
      return res.status(404).send({ error: 'Enrolment not found' });
    }

    const token = generateTokenForEnrolment(enrolment.id);

    res.json({
      participantId: enrolment.participantId,
      studyId: enrolment.studyId,
      token,
    });
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
