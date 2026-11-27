import { Express, NextFunction, Response } from 'express';
import * as z from 'zod';
import { authenticate } from '../middleware/authenticate';
import { Config } from '../config';
import { Observability } from '../o11y';

const ModelPath = z.object({ sha256: z.string().regex(/^[0-9a-f]{64}$/) });

/**
 * Serves the on-device content extraction model to the app, only with a valid
 * token (participants and admins). The study config names the file by its
 * SHA-256, the file is stored as <sha256>.litertlm in Config.app.modelLocation.
 * Range requests are supported, so the app can resume a download.
 *
 * Node streams the file itself. To let nginx send it instead, see "Content
 * extraction model" in the README (X-Accel-Redirect).
 */
export function createContentExtractionController(
  app: Express,
  observability: Observability,
) {
  app.get(
    '/v1/content-extraction/model/:sha256',
    authenticate,
    (req, res: Response, next: NextFunction) => {
      const params = ModelPath.safeParse(req.params);
      if (!params.success) {
        return res.status(400).send({ error: 'Invalid model hash' });
      }

      res.sendFile(
        `${params.data.sha256}.litertlm`,
        {
          root: Config.app.modelLocation,
          cacheControl: false,
          // only on the file: authenticated, so no shared caches; a hash never changes
          headers: { 'Cache-Control': 'private, max-age=31536000, immutable' },
        },
        (err?: Error & { status?: number; code?: string }) => {
          if (!err) return;
          if (err.status === 404) {
            observability.logger.warn('content extraction model not found', {
              sha256: params.data.sha256,
            });
            return res.status(404).send({ error: 'Model not found' });
          }
          if (res.headersSent) {
            // the app stopped or lost the download, it resumes with a range
            observability.logger.info(
              'content extraction model download aborted',
              {
                code: err.code,
              },
            );
            return;
          }
          next(err);
        },
      );
    },
  );

  observability.logger.info('loaded content extraction controller');
}
