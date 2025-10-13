import { Repository } from './repository';
import { DatabaseError } from '../config/errors';

type SensorType = string;
type SensorData = string;

export interface SensorReading {
  id: number;
  enrolmentId: number;
  sensorType: SensorType;
  data: SensorData;
  timestamp: string;
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
    readings: Pick<SensorReading, 'sensorType' | 'timestamp' | 'data'>[],
  ): Promise<SensorReading[]>;

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
  ): Promise<SensorReading[]> {
    try {
      const batched = await Promise.all(
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

      return batched
        .filter((res) => res.rows.length > 0)
        .map((res) => ({
          id: res.rows[0].id,
          localId: res.rows[0].local_id,
          enrolmentId: res.rows[0].enrolment_id,
          sensorType: res.rows[0].sensor_type,
          timestamp: res.rows[0].timestamp,
          data: res.rows[0].data,
        }));
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }
}
