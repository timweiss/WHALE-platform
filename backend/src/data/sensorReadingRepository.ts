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

export interface ISensorReadingRepository {
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
