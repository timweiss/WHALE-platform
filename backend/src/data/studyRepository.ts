import { Repository } from './repository';
import { DatabaseError } from '../config/errors';

export interface Study {
  id: number;
  name: string;
  enrolmentKey: string;
  maxEnrolments: number;
  durationDays: number;
}

enum InteractionWidgetStrategy {
  Default = 'Default',
  Bucketed = 'Bucketed',
}

enum StudyConfigurationAllocationType {
  Percentage = 'Percentage',
  Manual = 'Manual',
}

interface StudyConfigurationAllocation {
  type: StudyConfigurationAllocationType;
}

interface PercentageConfigurationAllocation
  extends StudyConfigurationAllocation {
  percentage: number;
}

export interface StudyConfiguration {
  id: number;
  studyId: number;

  internalName: string;
  allocation: StudyConfigurationAllocation;

  // configuration parameters
  interactionWidgetStrategy: InteractionWidgetStrategy;
}

export interface IStudyRepository {
  getStudies(): Promise<Study[]>;

  createStudy(study: Pick<Study, 'name' | 'enrolmentKey'>): Promise<Study>;

  getStudyById(id: number): Promise<Study | null>;

  getStudyByEnrolmentKey(enrolmentKey: string): Promise<Study | null>;

  updateStudy(study: Study): Promise<Study>;
}

export class StudyRepository extends Repository implements IStudyRepository {
  async createStudy(
    study: Pick<
      Study,
      'name' | 'enrolmentKey' | 'maxEnrolments' | 'durationDays'
    >,
  ): Promise<Study> {
    try {
      const res = await this.pool.query(
        'INSERT INTO studies (name, enrolment_key, max_enrolments, duration_days) VALUES ($1, $2, $3, $4) RETURNING *',
        [
          study.name,
          study.enrolmentKey,
          study.maxEnrolments,
          study.durationDays,
        ],
      );
      return {
        id: res.rows[0].id,
        name: res.rows[0].name,
        enrolmentKey: res.rows[0].enrolment_key,
        maxEnrolments: res.rows[0].max_enrolments,
        durationDays: res.rows[0].duration_days,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async updateStudy(study: Study): Promise<Study> {
    try {
      const res = await this.pool.query(
        'UPDATE studies SET name = $1, enrolment_key = $2, max_enrolments = $3, duration_days=$4 WHERE id = $5 RETURNING *',
        [
          study.name,
          study.enrolmentKey,
          study.maxEnrolments,
          study.durationDays,
          study.id,
        ],
      );
      return {
        id: res.rows[0].id,
        name: res.rows[0].name,
        enrolmentKey: res.rows[0].enrolment_key,
        maxEnrolments: res.rows[0].max_enrolments,
        durationDays: res.rows[0].duration_days,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getStudies(): Promise<Study[]> {
    try {
      const res = await this.pool.query('SELECT * FROM studies');
      return res.rows.map((row) => ({
        id: row.id,
        name: row.name,
        enrolmentKey: row.enrolment_key,
        maxEnrolments: row.max_enrolments,
        durationDays: row.duration_days,
      }));
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getStudyById(id: number): Promise<Study> {
    try {
      const res = await this.pool.query('SELECT * FROM studies WHERE id = $1', [
        id,
      ]);
      return {
        id: res.rows[0].id,
        name: res.rows[0].name,
        enrolmentKey: res.rows[0].enrolment_key,
        maxEnrolments: res.rows[0].max_enrolments,
        durationDays: res.rows[0].duration_days,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getStudyByEnrolmentKey(enrolmentKey: string): Promise<Study | null> {
    try {
      const res = await this.pool.query(
        'SELECT * FROM studies WHERE enrolment_key = $1',
        [enrolmentKey],
      );
      if (res.rows.length === 0) {
        return null;
      }
      return {
        id: res.rows[0].id,
        name: res.rows[0].name,
        enrolmentKey: res.rows[0].enrolment_key,
        maxEnrolments: res.rows[0].max_enrolments,
        durationDays: res.rows[0].duration_days,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }
}
