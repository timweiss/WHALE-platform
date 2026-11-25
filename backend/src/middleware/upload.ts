import multer, { MulterError } from 'multer';
import { NextFunction, Request, RequestHandler, Response } from 'express';

// screenshots are about 110 KB; nginx rejects bodies over 1 MB by default anyway
const MAX_FILE_BYTES = 1024 * 1024;
const MAX_FIELD_BYTES = 64 * 1024;

/**
 * Parses a multipart request with one file and one text field. The file is kept
 * in memory, so nothing is on disk before the request was validated.
 */
export function uploadSingle(fileField: string): RequestHandler {
  const parse = multer({
    storage: multer.memoryStorage(),
    limits: {
      fileSize: MAX_FILE_BYTES,
      fieldSize: MAX_FIELD_BYTES,
      files: 1,
      fields: 1,
      parts: 2,
    },
  }).single(fileField);

  return (req: Request, res: Response, next: NextFunction) => {
    parse(req, res, (err: unknown) => {
      if (!err) return next();
      if (err instanceof MulterError) {
        const status = err.code === 'LIMIT_FILE_SIZE' ? 413 : 400;
        return res.status(status).send({ error: err.message });
      }
      // multer passes on the parser's errors for malformed bodies without a status
      return res.status(400).send({ error: 'Invalid multipart request' });
    });
  };
}
