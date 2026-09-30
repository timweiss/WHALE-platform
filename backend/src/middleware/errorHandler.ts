import { ErrorRequestHandler } from 'express';
import { Observability } from '../o11y';

/**
 * Last middleware in the chain. Express 5 forwards errors thrown in (async)
 * route handlers here, so they end in a response instead of an unhandled
 * rejection. Internal details are only logged, never sent to the client.
 */
export function createErrorHandler(
  observability: Observability,
): ErrorRequestHandler {
  return (err, req, res, next) => {
    if (res.headersSent) {
      return next(err);
    }

    // client errors raised by express itself, e.g. malformed JSON (400) or a
    // body over the size limit (413)
    if (
      typeof err?.status === 'number' &&
      err.status >= 400 &&
      err.status < 500
    ) {
      return res
        .status(err.status)
        .send({ error: err.expose ? err.message : 'Invalid request' });
    }

    // the route template, not the path: paths can carry study keys
    // (/v1/study/:idOrKey)
    observability.logger.error('Unhandled error while processing request', {
      method: req.method,
      route: req.route ? `${req.baseUrl}${req.route.path}` : undefined,
      error: err instanceof Error ? err.message : String(err),
    });

    return res.status(500).send({ error: 'Internal server error' });
  };
}
