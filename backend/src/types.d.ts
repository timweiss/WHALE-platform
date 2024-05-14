import { RequestUser } from './middleware/authenticate';

declare global {
  namespace Express {
    interface Request {
      user?: RequestUser | string;
    }
  }
}
