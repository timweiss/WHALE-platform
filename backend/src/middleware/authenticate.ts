import jwt, { JwtPayload } from 'jsonwebtoken';
import { Config } from '../config';
import { NextFunction, Request, Response } from 'express';

type UserRole = 'participant' | 'admin';

export interface UserPayload {
  role: UserRole;
  enrolmentId?: number;
}

export type RequestUser = UserPayload | JwtPayload;

export const authenticate = async (
  req: Request,
  res: Response,
  next: NextFunction,
) => {
  const authHeader = req.get('Authorization');

  if (authHeader) {
    const token = authHeader.replace('Bearer ', '');
    try {
      const data = jwt.verify(token, Config.auth.jwtSecret);
      if (!data) {
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
