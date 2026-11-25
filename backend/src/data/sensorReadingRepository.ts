import { Repository } from './repository';
import { DatabaseError } from '../config/errors';

type SensorType = string;
type SensorData = string;

export interface SensorReading {
  id: number;
  enrolmentId: number;
  sensorType: SensorType;
  data: SensorData;
  timestamp: number;
  localId: string;
}

export interface UploadFile {
  id: number;
  readingId: number;
  filename: string;
  /** relative to the upload location */
  path: string;
}

export interface ISensorReadingRepository {
  createSensorReadingWithFile(
    enrolmentId: number,
    reading: Pick<
      SensorReading,
      'localId' | 'sensorType' | 'timestamp' | 'data'
    >,
    file: Pick<UploadFile, 'filename' | 'path'>,
  ): Promise<void>;

  createSensorReadingBatched(
    enrolmentId: number,
    readings: Pick<
      SensorReading,
      'localId' | 'sensorType' | 'timestamp' | 'data'
    >[],
  ): Promise<void>;

  createSensorReadingBulk(
    enrolmentId: number,
    readings: Pick<
      SensorReading,
      'localId' | 'sensorType' | 'timestamp' | 'data'
    >[],
  ): Promise<void>;
}

export class SensorReadingRepository
  extends Repository
  implements ISensorReadingRepository
{
  /**
   * Inserts the reading and its file in one statement, so there is never one
   * without the other. Safe to repeat: the update changes nothing, but makes the
   * existing reading's id available.
   */
  async createSensorReadingWithFile(
    enrolmentId: number,
    reading: Pick<
      SensorReading,
      'sensorType' | 'timestamp' | 'data' | 'localId'
    >,
    file: Pick<UploadFile, 'filename' | 'path'>,
  ): Promise<void> {
    try {
      await this.pool.query(
        `WITH reading AS (
           INSERT INTO sensor_readings (enrolment_id, local_id, sensor_type, timestamp, data)
           VALUES ($1, $2, $3, $4, $5)
           ON CONFLICT (enrolment_id, local_id) DO UPDATE SET local_id = EXCLUDED.local_id
           RETURNING id
         )
         INSERT INTO upload_files (reading_id, filename, path)
         SELECT reading.id, $6, $7 FROM reading
         WHERE NOT EXISTS (SELECT 1 FROM upload_files WHERE reading_id = reading.id)`,
        [
          enrolmentId,
          reading.localId,
          reading.sensorType,
          reading.timestamp,
          reading.data,
          file.filename,
          file.path,
        ],
      );
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async createSensorReadingBatched(
    enrolmentId: number,
    readings: Pick<
      SensorReading,
      'sensorType' | 'timestamp' | 'data' | 'localId'
    >[],
  ): Promise<void> {
    try {
      await Promise.all(
        readings.map((reading) =>
          this.pool.query(
            'INSERT INTO sensor_readings (enrolment_id, local_id, sensor_type, timestamp, data) VALUES ($1, $2, $3, $4, $5) ON CONFLICT (enrolment_id, local_id) DO NOTHING RETURNING *',
            [
              enrolmentId,
              reading.localId,
              reading.sensorType,
              reading.timestamp,
              reading.data,
            ],
          ),
        ),
      );

      return;
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  /**
   * This method inserts all readings in a single query instead of multiple queries.
   */
  async createSensorReadingBulk(
    enrolmentId: number,
    readings: Pick<
      SensorReading,
      'sensorType' | 'timestamp' | 'data' | 'localId'
    >[],
  ): Promise<void> {
    if (readings.length === 0) {
      return;
    }

    try {
      // Build arrays for each column
      const enrolmentIds = readings.map(() => enrolmentId);
      const localIds = readings.map((r) => r.localId);
      const sensorTypes = readings.map((r) => r.sensorType);
      const timestamps = readings.map((r) => r.timestamp);
      const data = readings.map((r) => r.data);

      // Use unnest to insert all rows in a single query
      await this.pool.query(
        `INSERT INTO sensor_readings (enrolment_id, local_id, sensor_type, timestamp, data)
         SELECT * FROM unnest($1::int[], $2::uuid[], $3::text[], $4::bigint[], $5::text[])
         ON CONFLICT (enrolment_id, local_id) DO NOTHING`,
        [enrolmentIds, localIds, sensorTypes, timestamps, data],
      );

      return;
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }
}
