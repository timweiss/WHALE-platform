import multer, { FileFilterCallback } from 'multer';
import { Config } from '../config';
import { Request } from 'express';

const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, Config.app.uploadLocation);
  },
  filename: (req, file, cb) => {
    cb(null, `${Date.now()}-${file.originalname}`);
  },
});

const fileFilter = (
  req: Request,
  file: Express.Multer.File,
  cb: FileFilterCallback,
) => {
  if (file.mimetype === 'audio/aac') {
    cb(null, true);
  } else {
    cb(null, false);
  }
};

export const upload = multer({
  storage: storage,
  fileFilter: fileFilter,
});
