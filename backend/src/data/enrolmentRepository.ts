import { Repository } from './repository';
import { DatabaseError } from '../config/errors';

export interface Enrolment {
  id: number;
  studyId: number;
  participantId: string;
  studyConfigurationId: number;
}

export interface IEnrolmentRepository {
  getEnrolmentCountByStudyId(studyId: number): Promise<number>;

  createEnrolment(
    enrolment: Pick<Enrolment, 'studyId' | 'participantId'>,
  ): Promise<Enrolment>;

  getEnrolmentByParticipantId(participantId: string): Promise<Enrolment | null>;

  getEnrolmentById(id: number): Promise<Enrolment | null>;
}

export class EnrolmentRepository
  extends Repository
  implements IEnrolmentRepository
{
  async getEnrolmentById(id: number): Promise<Enrolment | null> {
    try {
      const res = await this.pool.query(
        'SELECT * FROM enrolments WHERE id = $1',
        [id],
      );
      if (res.rows.length === 0) {
        return null;
      }
      return {
        id: res.rows[0].id,
        studyId: res.rows[0].study_id,
        participantId: res.rows[0].participant_id,
        studyConfigurationId: res.rows[0].study_configuration_id,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getEnrolmentCountByStudyId(studyId: number): Promise<number> {
    try {
      const res = await this.pool.query(
        'SELECT COUNT(*) FROM enrolments WHERE study_id = $1',
        [studyId],
      );
      return parseInt(res.rows[0].count);
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async createEnrolment(
    enrolment: Pick<Enrolment, 'studyId' | 'participantId'>,
  ): Promise<Enrolment> {
    try {
      const res = await this.pool.query(
        'INSERT INTO enrolments (study_id, participant_id) VALUES ($1, $2) RETURNING *',
        [enrolment.studyId, enrolment.participantId],
      );
      return {
        id: res.rows[0].id,
        studyId: res.rows[0].study_id,
        participantId: res.rows[0].participant_id,
        studyConfigurationId: res.rows[0].study_configuration_id,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getEnrolmentByParticipantId(
    participantId: string,
  ): Promise<Enrolment | null> {
    try {
      const res = await this.pool.query(
        'SELECT * FROM enrolments WHERE participant_id = $1',
        [participantId],
      );
      if (res.rows.length === 0) {
        return null;
      }
      return {
        id: res.rows[0].id,
        studyId: res.rows[0].study_id,
        participantId: res.rows[0].participant_id,
        studyConfigurationId: res.rows[0].study_configuration_id,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }
}
