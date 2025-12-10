import { Repository } from './repository';
import { DatabaseError } from '../config/errors';

export interface Enrolment {
  id: number;
  studyId: number;
  participantId: string;
  studyExperimentalGroupId: number;
  enrolledAt: Date;
  debugEnabled: boolean;
  source: string | null;
  additionalInformation: string | null;
}

interface EnrolmentRow {
  id: number;
  study_id: number;
  participant_id: string;
  study_experimental_group_id: number;
  enrolled_at: Date;
  debug_enabled: boolean;
  source: string | null;
  additional_information: string | null;
}

export interface IEnrolmentRepository {
  getEnrolmentCountByStudyId(studyId: number): Promise<number>;

  createEnrolment(
    enrolment: Pick<
      Enrolment,
      'studyId' | 'participantId' | 'studyExperimentalGroupId' | 'source'
    >,
  ): Promise<Enrolment>;

  getEnrolmentByParticipantId(participantId: string): Promise<Enrolment | null>;

  getEnrolmentById(id: number): Promise<Enrolment | null>;

  getLastEnrolmentByStudyId(studyId: number): Promise<Enrolment | null>;
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
      return this.enrolmentFromRow(res.rows[0]);
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
    enrolment: Pick<
      Enrolment,
      'studyId' | 'participantId' | 'studyExperimentalGroupId' | 'source'
    >,
  ): Promise<Enrolment> {
    try {
      const res = await this.pool.query(
        'INSERT INTO enrolments (study_id, participant_id, study_experimental_group_id, source) VALUES ($1, $2, $3, $4) RETURNING *',
        [
          enrolment.studyId,
          enrolment.participantId,
          enrolment.studyExperimentalGroupId,
          enrolment.source,
        ],
      );
      return this.enrolmentFromRow(res.rows[0]);
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
      return this.enrolmentFromRow(res.rows[0]);
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getLastEnrolmentByStudyId(studyId: number): Promise<Enrolment | null> {
    try {
      const res = await this.pool.query(
        'SELECT * FROM enrolments WHERE study_id = $1 ORDER BY enrolled_at DESC LIMIT 1',
        [studyId],
      );
      if (res.rows.length === 0) {
        return null;
      }
      return this.enrolmentFromRow(res.rows[0]);
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  private enrolmentFromRow(row: EnrolmentRow): Enrolment {
    return {
      id: row.id,
      studyId: row.study_id,
      participantId: row.participant_id,
      studyExperimentalGroupId: row.study_experimental_group_id,
      enrolledAt: row.enrolled_at,
      debugEnabled: row.debug_enabled,
      source: row.source,
      additionalInformation: row.additional_information
    };
  }
}
