import { Repository } from './repository';
import { DatabaseError } from '../config/errors';

export interface ICompletionRepository {
  getCountOfDaysWithSensorData(enrolmentId: number): Promise<number>;
  getCountOfEMAsAnswered(enrolmentId: number): Promise<number>;
}

export class CompletionRepository
  extends Repository
  implements ICompletionRepository
{
  async getCountOfDaysWithSensorData(enrolmentId: number): Promise<number> {
    try {
      const res = await this.pool.query(
        'SELECT COUNT(DISTINCT DATE(to_timestamp(timestamp::bigint / 1000))) AS distinct_day_count FROM sensor_readings where enrolment_id=$1;',
        [enrolmentId],
      );
      return res.rows[0].distinct_day_count;
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getCountOfEMAsAnswered(enrolmentId: number): Promise<number> {
    try {
      const res = await this.pool.query(
        'SELECT COUNT(*) AS count FROM esm_answers WHERE enrolment_id=$1;',
        [enrolmentId],
      );
      return res.rows[0].count;
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }
}
