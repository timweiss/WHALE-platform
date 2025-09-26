import { Repository } from './repository';
import { DatabaseError } from '../config/errors';

export interface Study {
  id: number;
  name: string;
  enrolmentKey: string;
  description: string;
  contactEmail: string;
  maxEnrolments: number;
  durationDays: number;
  allocationStrategy: StudyExperimentalGroupAllocationStrategy;
  completionTracking: CompletionTracking | null;
  dataProtectionNotice: string | null;
}

interface StudyRow {
  id: number;
  name: string;
  enrolment_key: string;
  description: string;
  contact_email: string;
  max_enrolments: number;
  duration_days: number;
  allocation_strategy: StudyExperimentalGroupAllocationStrategy;
  completion_tracking: CompletionTracking | null;
  data_protection_notice: string | null;
}

enum InteractionWidgetStrategy {
  Default = 'Default',
  Bucketed = 'Bucketed',
}

enum StudyExperimentalGroupAllocationStrategy {
  Sequential = 'Sequential',
  First = 'First',
}

export interface StudyExperimentalGroup {
  id: number;
  studyId: number;

  allocationOrder: number;
  internalName: string;
}

export interface StudyExperimentalGroupPhase {
  experimentalGroupId: number;
  name: string;

  fromDay: number;
  durationDays: number;

  // configuration parameters
  interactionWidgetStrategy: InteractionWidgetStrategy;
}

type CompletionTracking = {
  [key: string]: CompletionItem[];
};

export enum CompletionItemType {
  PassiveSensingParticipationDays = 'PassiveSensingParticipationDays',
  EMAAnswered = 'EMAAnswered',
}

export interface CompletionItem {
  type: CompletionItemType;
}

export interface PassiveSensingParticipationDaysCompletionItem
  extends CompletionItem {
  type: CompletionItemType.PassiveSensingParticipationDays;
  value: number;
}

export interface EMAAnsweredCompletionItem extends CompletionItem {
  type: CompletionItemType.EMAAnswered;
  value: number;
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
      'studyId' | 'internalName' | 'allocationOrder'
    >,
  ): Promise<StudyExperimentalGroup>;

  getExperimentalGroupsByStudyId(
    studyId: number,
  ): Promise<StudyExperimentalGroup[]>;

  getExperimentalGroupById(id: number): Promise<StudyExperimentalGroup | null>;

  createExperimentalGroupPhase(
    phase: Pick<
      StudyExperimentalGroupPhase,
      | 'experimentalGroupId'
      | 'name'
      | 'fromDay'
      | 'durationDays'
      | 'interactionWidgetStrategy'
    >,
  ): Promise<StudyExperimentalGroupPhase>;

  getExperimentalGroupPhasesByExperimentalGroupId(
    experimentalGroupId: number,
  ): Promise<StudyExperimentalGroupPhase[]>;
}

export class StudyRepository extends Repository implements IStudyRepository {
  async createStudy(
    study: Pick<
      Study,
      | 'name'
      | 'enrolmentKey'
      | 'description'
      | 'contactEmail'
      | 'maxEnrolments'
      | 'durationDays'
      | 'allocationStrategy'
      | 'completionTracking'
    >,
  ): Promise<Study> {
    try {
      const res = await this.pool.query(
        'INSERT INTO studies (name, enrolment_key, description, contact_email, max_enrolments, duration_days, allocation_strategy, completion_tracking) VALUES ($1, $2, $3, $4, $5, $6, $7, $8) RETURNING *',
        [
          study.name,
          study.enrolmentKey,
          study.description,
          study.contactEmail,
          study.maxEnrolments,
          study.durationDays,
          study.allocationStrategy,
          study.completionTracking,
        ],
      );
      return this.studyFromRow(res.rows[0]);
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async updateStudy(study: Study): Promise<Study> {
    try {
      const res = await this.pool.query(
        'UPDATE studies SET name = $1, enrolment_key = $2, max_enrolments = $3, duration_days=$4, allocation_strategy=$5, completion_tracking=$6, description=$7, contact_email=$8 WHERE id = $9 RETURNING *',
        [
          study.name,
          study.enrolmentKey,
          study.maxEnrolments,
          study.durationDays,
          study.allocationStrategy,
          study.completionTracking,
          study.description,
          study.contactEmail,
          study.id,
        ],
      );
      return this.studyFromRow(res.rows[0]);
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getStudies(): Promise<Study[]> {
    try {
      const res = await this.pool.query('SELECT * FROM studies');
      return res.rows.map((row) => this.studyFromRow(row));
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

      return this.studyFromRow(res.rows[0]);
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
      return this.studyFromRow(res.rows[0]);
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  private studyFromRow(row: StudyRow): Study {
    return {
      id: row.id,
      name: row.name,
      enrolmentKey: row.enrolment_key,
      description: row.description,
      contactEmail: row.contact_email,
      maxEnrolments: row.max_enrolments,
      durationDays: row.duration_days,
      allocationStrategy: row.allocation_strategy,
      completionTracking: row.completion_tracking,
      dataProtectionNotice: row.data_protection_notice,
    };
  }

  // Study Configuration

  async createExperimentalGroup(
    group: Pick<
      StudyExperimentalGroup,
      'studyId' | 'internalName' | 'allocationOrder'
    >,
  ): Promise<StudyExperimentalGroup> {
    try {
      const res = await this.pool.query(
        'INSERT INTO study_experimental_groups (internal_name, allocation_order, study_id) VALUES ($1, $2, $3) RETURNING *',
        [group.internalName, group.allocationOrder, group.studyId],
      );

      return {
        id: res.rows[0].id,
        internalName: res.rows[0].internal_name,
        allocationOrder: res.rows[0].allocation_order,
        studyId: res.rows[0].study_id,
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
        allocationOrder: row.allocation_order,
        studyId: row.study_id,
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
        allocationOrder: res.rows[0].allocation_order,
        studyId: res.rows[0].study_id,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async createExperimentalGroupPhase(
    phase: Pick<
      StudyExperimentalGroupPhase,
      | 'experimentalGroupId'
      | 'name'
      | 'fromDay'
      | 'durationDays'
      | 'interactionWidgetStrategy'
    >,
  ): Promise<StudyExperimentalGroupPhase> {
    try {
      const res = await this.pool.query(
        'INSERT INTO study_experimental_group_phases (experimental_group_id, internal_name, from_day, duration_days, interaction_widget_strategy) VALUES ($1, $2, $3, $4, $5) RETURNING *',
        [
          phase.experimentalGroupId,
          phase.name,
          phase.fromDay,
          phase.durationDays,
          phase.interactionWidgetStrategy,
        ],
      );

      return {
        experimentalGroupId: res.rows[0].experimental_group_id,
        name: res.rows[0].internal_name,
        fromDay: res.rows[0].from_day,
        durationDays: res.rows[0].duration_days,
        interactionWidgetStrategy: res.rows[0].interaction_widget_strategy,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getExperimentalGroupPhasesByExperimentalGroupId(
    experimentalGroupId: number,
  ): Promise<StudyExperimentalGroupPhase[]> {
    try {
      const res = await this.pool.query(
        'SELECT * FROM study_experimental_group_phases WHERE experimental_group_id = $1',
        [experimentalGroupId],
      );

      return res.rows.map((row) => ({
        experimentalGroupId: row.experimental_group_id,
        name: row.internal_name,
        fromDay: row.from_day,
        durationDays: row.duration_days,
        interactionWidgetStrategy: row.interaction_widget_strategy,
      }));
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }
}
