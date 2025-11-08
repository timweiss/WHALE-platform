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
  path: string;
}

export interface ISensorReadingRepository {
  createSensorReading(
    enrolmentId: number,
    reading: Pick<
      SensorReading,
      'sensorType' | 'data' | 'timestamp' | 'localId'
    >,
  ): Promise<SensorReading | null>;

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

  createFile(
    readingId: number,
    file: Pick<UploadFile, 'filename' | 'path'>,
  ): Promise<UploadFile>;
}

export class SensorReadingRepository
  extends Repository
  implements ISensorReadingRepository
{
  async createFile(
    readingId: number,
    file: Pick<UploadFile, 'filename' | 'path'>,
  ): Promise<UploadFile> {
    try {
      const res = await this.pool.query(
        'INSERT INTO upload_files (reading_id, filename, path) VALUES ($1, $2, $3) RETURNING *',
        [readingId, file.filename, file.path],
      );
      return {
        id: res.rows[0].id,
        readingId: res.rows[0].reading_id,
        filename: res.rows[0].filename,
        path: res.rows[0].path,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async createSensorReading(
    enrolmentId: number,
    reading: Pick<
      SensorReading,
      'sensorType' | 'timestamp' | 'data' | 'localId'
    >,
  ): Promise<SensorReading | null> {
    try {
      const res = await this.pool.query(
        'INSERT INTO sensor_readings (enrolment_id, local_id, sensor_type, timestamp, data) VALUES ($1, $2, $3, $4, $5) ON CONFLICT (enrolment_id, local_id) DO NOTHING RETURNING *',
        [
          enrolmentId,
          reading.localId,
          reading.sensorType,
          reading.timestamp,
          reading.data,
        ],
      );

      if (res.rows.length === 0) {
        return null;
      }

      return {
        id: res.rows[0].id,
        localId: res.rows[0].local_id,
        enrolmentId: res.rows[0].enrolment_id,
        sensorType: res.rows[0].sensor_type,
        timestamp: res.rows[0].timestamp,
        data: res.rows[0].data,
      };
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
