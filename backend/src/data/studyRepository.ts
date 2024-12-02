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

enum StudyExperimentalGroupAllocationType {
  Percentage = 'Percentage',
  Manual = 'Manual',
}

interface StudyExperimentalGroupAllocation {
  type: StudyExperimentalGroupAllocationType;
}

export interface PercentageGroupAllocation
  extends StudyExperimentalGroupAllocation {
  percentage: number;
}

export interface StudyExperimentalGroup {
  id: number;
  studyId: number;

  internalName: string;
  allocation: StudyExperimentalGroupAllocation;

  // configuration parameters
  interactionWidgetStrategy: InteractionWidgetStrategy;
}

export interface IStudyRepository {
  getStudies(): Promise<Study[]>;

  createStudy(study: Pick<Study, 'name' | 'enrolmentKey'>): Promise<Study>;

  getStudyById(id: number): Promise<Study | null>;

  getStudyByEnrolmentKey(enrolmentKey: string): Promise<Study | null>;

  updateStudy(study: Study): Promise<Study>;

  createExperimentalGroup(
    group: Pick<
      StudyExperimentalGroup,
      'studyId' | 'allocation' | 'internalName' | 'interactionWidgetStrategy'
    >,
  ): Promise<StudyExperimentalGroup>;

  getExperimentalGroupsByStudyId(
    studyId: number,
  ): Promise<StudyExperimentalGroup[]>;

  getExperimentalGroupById(id: number): Promise<StudyExperimentalGroup | null>;
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

  async getStudyById(id: number): Promise<Study | null> {
    try {
      const res = await this.pool.query('SELECT * FROM studies WHERE id = $1', [
        id,
      ]);

      if (res.rowCount === 0) {
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

  // Study Configuration

  async createExperimentalGroup(
    group: Pick<
      StudyExperimentalGroup,
      'studyId' | 'allocation' | 'internalName' | 'interactionWidgetStrategy'
    >,
  ): Promise<StudyExperimentalGroup> {
    try {
      const res = await this.pool.query(
        'INSERT INTO study_experimental_groups (internal_name, study_id, allocation, interaction_widget_strategy) VALUES ($1, $2, $3, $4) RETURNING *',
        [
          group.internalName,
          group.studyId,
          group.allocation,
          group.interactionWidgetStrategy,
        ],
      );

      return {
        id: res.rows[0].id,
        internalName: res.rows[0].internal_name,
        studyId: res.rows[0].study_id,
        allocation: res.rows[0].allocation,
        interactionWidgetStrategy: res.rows[0].interaction_widget_strategy,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getExperimentalGroupsByStudyId(
    studyId: number,
  ): Promise<StudyExperimentalGroup[]> {
    try {
      const res = await this.pool.query(
        'SELECT * FROM study_experimental_groups WHERE study_id = $1',
        [studyId],
      );
      return res.rows.map((row) => ({
        id: row.id,
        internalName: row.internal_name,
        studyId: row.study_id,
        allocation: row.allocation,
        interactionWidgetStrategy: row.interaction_widget_strategy,
      }));
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getExperimentalGroupById(
    id: number,
  ): Promise<StudyExperimentalGroup | null> {
    try {
      const res = await this.pool.query(
        'SELECT * FROM study_experimental_groups WHERE id = $1',
        [id],
      );
      if (res.rows.length === 0) {
        return null;
      }
      return {
        id: res.rows[0].id,
        internalName: res.rows[0].internal_name,
        studyId: res.rows[0].study_id,
        allocation: res.rows[0].allocation,
        interactionWidgetStrategy: res.rows[0].interaction_widget_strategy,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }
}
