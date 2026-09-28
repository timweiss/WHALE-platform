import jwt, { JwtPayload } from 'jsonwebtoken';
import { Config } from '../config';
import { NextFunction, Request, Response } from 'express';

type UserRole = 'participant' | 'admin';

export interface UserPayload {
  role: UserRole;
  enrolmentId?: number;
}

export type RequestUser = UserPayload | JwtPayload;

// Tokens issued before wave 2 set `iat` in milliseconds, which pushed their
// expiry tens of thousands of years into the future. A seconds-based `iat`
// stays below this threshold until the year 5138, so anything above it is
// one of those tokens and is no longer accepted.
const MILLISECOND_IAT_THRESHOLD = 1e11;

function isLegacyToken(data: string | JwtPayload) {
  return (
    typeof data === 'object' &&
    typeof data.iat === 'number' &&
    data.iat > MILLISECOND_IAT_THRESHOLD
  );
}

export const authenticate = async (
  req: Request,
  res: Response,
  next: NextFunction,
) => {
  const authHeader = req.get('Authorization');

  if (authHeader) {
    const token = authHeader.replace('Bearer ', '');
    try {
      const data = jwt.verify(token, Config.auth.jwtSecret, {
        algorithms: ['HS256'],
      });
      if (!data || isLegacyToken(data)) {
        return res
          .status(401)
          .send({ error: 'Not authorized to access this resource' });
      }
      req.user = data;
      next();
    } catch (e) {
      console.log('error verifying token' + e);
      return res.sendStatus(401);
    }
  } else {
    return res.sendStatus(401);
  }
};

export const requireAdmin = async (
  req: Request,
  res: Response,
  next: NextFunction,
) => {
  const user = req.user as RequestUser;
  if (user.role !== 'admin') {
    return res.sendStatus(403);
  }
  next();
};
