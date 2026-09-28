import { Request } from 'express';
import { rateLimit } from 'express-rate-limit';

/**
 * Limits requests per client IP and hour. Counters are kept in memory, which
 * is fine as long as a single API process runs. Behind a reverse proxy, the
 * client IP is only correct if `trust proxy` is configured (APP_TRUST_PROXY).
 */
export function createHourlyRateLimit(
  limit: number,
  skip?: (req: Request) => boolean,
) {
  return rateLimit({
    windowMs: 60 * 60 * 1000,
    limit,
    skip,
    standardHeaders: 'draft-8',
    legacyHeaders: false,
    message: { error: 'Too many requests', code: 'rate_limited' },
  });
}
