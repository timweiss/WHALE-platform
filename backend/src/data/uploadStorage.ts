import { mkdir, rename, rm, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import path from 'node:path';
import { Config } from '../config';

/**
 * Writes the file below the upload location. Renamed into place, so an existing
 * file is never seen half-written.
 *
 * @param relativePath must not contain unvalidated client input
 */
export async function storeUpload(relativePath: string, content: Buffer) {
  const target = path.join(Config.app.uploadLocation, relativePath);
  const temporary = `${target}.${randomUUID()}.tmp`;

  await mkdir(path.dirname(target), { recursive: true });
  try {
    await writeFile(temporary, content);
    await rename(temporary, target);
  } catch (e) {
    await rm(temporary, { force: true });
    throw e;
  }
}
