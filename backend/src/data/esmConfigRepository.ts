import { Repository } from './repository';
import { DatabaseError } from '../config/errors';
import { Rule } from '../model/questionnaire-rules';

export interface ExperienceSamplingQuestionnaire {
  id: number;
  studyId: number;
  name: string;
  enabled: boolean;
  version: number;
  rules: Rule[] | null;
}

type ElementType = string;

export interface ElementConfiguration {}

export interface ExperienceSamplingElement {
  id: number;
  questionnaireId: number;
  name: string;
  type: ElementType;
  step: number;
  position: number;
  configuration: ElementConfiguration;
}

type TriggerType = string;

export interface TriggerConfiguration {}

export interface ExperienceSamplingTrigger {
  id: number;
  questionnaireId: number;
  type: TriggerType;
  validDuration: number;
  configuration: TriggerConfiguration;
  enabled: boolean;
}

export interface IESMConfigRepository {
  getESMQuestionnaireById(id: number): Promise<ExperienceSamplingQuestionnaire>;

  getESMQuestionnairesByStudyId(
    studyId: number,
  ): Promise<ExperienceSamplingQuestionnaire[]>;

  createESMQuestionnaire(
    questionnaire: Pick<
      ExperienceSamplingQuestionnaire,
      'studyId' | 'name' | 'enabled'
    >,
  ): Promise<ExperienceSamplingQuestionnaire>;

  updateESMQuestionnaire(
    questionnaire: ExperienceSamplingQuestionnaire,
  ): Promise<ExperienceSamplingQuestionnaire>;

  getESMQuestionnaireTriggersByQuestionnaireId(
    questionnaireId: number,
  ): Promise<ExperienceSamplingTrigger[]>;

  createESMQuestionnaireTrigger(
    trigger: Pick<
      ExperienceSamplingTrigger,
      'questionnaireId' | 'type' | 'validDuration' | 'configuration' | 'enabled'
    >,
  ): Promise<ExperienceSamplingTrigger>;

  updateESMQuestionnaireTrigger(
    trigger: ExperienceSamplingTrigger,
  ): Promise<ExperienceSamplingTrigger>;

  deleteESMQuestionnaireTrigger(id: number): Promise<void>;

  getESMElementsByQuestionnaireId(
    questionnaireId: number,
  ): Promise<ExperienceSamplingElement[]>;

  createESMElement(
    element: Pick<
      ExperienceSamplingElement,
      | 'questionnaireId'
      | 'name'
      | 'type'
      | 'step'
      | 'position'
      | 'configuration'
    >,
  ): Promise<ExperienceSamplingElement>;

  updateESMElement(
    element: ExperienceSamplingElement,
  ): Promise<ExperienceSamplingElement>;

  deleteESMElement(id: number): Promise<void>;
}

export class ESMConfigRepository
  extends Repository
  implements IESMConfigRepository
{
  async getESMQuestionnairesByStudyId(
    studyId: number,
  ): Promise<ExperienceSamplingQuestionnaire[]> {
    try {
      const questionnaires = await this.pool.query(
        'SELECT * FROM esm_questionnaires WHERE study_id = $1',
        [studyId],
      );

      return questionnaires.rows.map((row) => ({
        id: row.id,
        studyId: row.study_id,
        name: row.name,
        enabled: row.enabled,
        version: row.version,
        rules: row.rules,
      }));
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async createESMQuestionnaire(
    questionnaire: Pick<
      ExperienceSamplingQuestionnaire,
      'name' | 'studyId' | 'enabled'
    >,
  ): Promise<ExperienceSamplingQuestionnaire> {
    try {
      const res = await this.pool.query(
        'INSERT INTO esm_questionnaires (name, study_id, enabled, version) VALUES ($1, $2, $3, 1) RETURNING *',
        [questionnaire.name, questionnaire.studyId, questionnaire.enabled],
      );

      return {
        id: res.rows[0].id,
        studyId: res.rows[0].study_id,
        name: res.rows[0].name,
        enabled: res.rows[0].enabled,
        version: res.rows[0].version,
        rules: res.rows[0].rules,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getESMQuestionnaireById(
    id: number,
  ): Promise<ExperienceSamplingQuestionnaire> {
    try {
      const questionnaire = await this.pool.query(
        'SELECT * FROM esm_questionnaires WHERE id = $1',
        [id],
      );

      return {
        id: questionnaire.rows[0].id,
        studyId: questionnaire.rows[0].study_id,
        name: questionnaire.rows[0].name,
        enabled: questionnaire.rows[0].enabled,
        version: questionnaire.rows[0].version,
        rules: questionnaire.rows[0].rules,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async updateESMQuestionnaire(
    questionnaire: ExperienceSamplingQuestionnaire,
  ): Promise<ExperienceSamplingQuestionnaire> {
    try {
      const existing = await this.getESMQuestionnaireById(questionnaire.id);

      const newVersion =
        existing.version === questionnaire.version
          ? existing.version + 1
          : questionnaire.version;

      const updated = await this.pool.query(
        'UPDATE esm_questionnaires SET name = $1, enabled = $2, rules=$3 WHERE id = $4 RETURNING *',
        [
          questionnaire.name,
          questionnaire.enabled,
          questionnaire.rules,
          questionnaire.id,
        ],
      );

      return {
        id: updated.rows[0].id,
        studyId: updated.rows[0].study_id,
        name: updated.rows[0].name,
        enabled: updated.rows[0].enabled,
        version: newVersion,
        rules: updated.rows[0].rules,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getESMQuestionnaireTriggersByQuestionnaireId(
    questionnaireId: number,
  ): Promise<ExperienceSamplingTrigger[]> {
    try {
      const triggers = await this.pool.query(
        'SELECT * FROM esm_triggers WHERE questionnaire_id = $1',
        [questionnaireId],
      );

      return triggers.rows.map((row) => ({
        id: row.id,
        questionnaireId: row.questionnaire_id,
        type: row.type,
        validDuration: row.valid_duration,
        configuration: row.configuration,
        enabled: row.enabled,
      }));
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async createESMQuestionnaireTrigger(
    trigger: Pick<
      ExperienceSamplingTrigger,
      'enabled' | 'questionnaireId' | 'type' | 'validDuration' | 'configuration'
    >,
  ): Promise<ExperienceSamplingTrigger> {
    try {
      const res = await this.pool.query(
        'INSERT INTO esm_triggers (questionnaire_id, type, valid_duration, configuration, enabled) VALUES ($1, $2, $3, $4, $5) RETURNING *',
        [
          trigger.questionnaireId,
          trigger.type,
          trigger.validDuration,
          trigger.configuration,
          trigger.enabled,
        ],
      );

      return {
        id: res.rows[0].id,
        questionnaireId: res.rows[0].questionnaire_id,
        type: res.rows[0].type,
        validDuration: res.rows[0].valid_duration,
        configuration: res.rows[0].configuration,
        enabled: res.rows[0].enabled,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async updateESMQuestionnaireTrigger(
    trigger: ExperienceSamplingTrigger,
  ): Promise<ExperienceSamplingTrigger> {
    try {
      const updated = await this.pool.query(
        'UPDATE esm_triggers SET type = $1, configuration = $2, enabled = $3, valid_duration=$4 WHERE id = $5 RETURNING *',
        [
          trigger.type,
          trigger.configuration,
          trigger.enabled,
          trigger.validDuration,
          trigger.id,
        ],
      );

      return {
        id: updated.rows[0].id,
        questionnaireId: updated.rows[0].questionnaire_id,
        type: updated.rows[0].type,
        validDuration: updated.rows[0].valid_duration,
        configuration: updated.rows[0].configuration,
        enabled: updated.rows[0].enabled,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async deleteESMQuestionnaireTrigger(id: number): Promise<void> {
    try {
      await this.pool.query('DELETE FROM esm_triggers WHERE id = $1', [id]);
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getESMElementsByQuestionnaireId(
    questionnaireId: number,
  ): Promise<ExperienceSamplingElement[]> {
    try {
      const elements = await this.pool.query(
        'SELECT * FROM esm_elements WHERE questionnaire_id = $1',
        [questionnaireId],
      );

      return elements.rows.map((row) => ({
        id: row.id,
        questionnaireId: row.questionnaire_id,
        name: row.name,
        type: row.type,
        step: row.step,
        position: row.position,
        configuration: row.configuration,
      }));
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async createESMElement(
    element: Pick<
      ExperienceSamplingElement,
      | 'questionnaireId'
      | 'name'
      | 'type'
      | 'step'
      | 'position'
      | 'configuration'
    >,
  ): Promise<ExperienceSamplingElement> {
    try {
      const res = await this.pool.query(
        'INSERT INTO esm_elements (questionnaire_id, name, type, step, position, configuration) VALUES ($1, $2, $3, $4, $5, $6) RETURNING *',
        [
          element.questionnaireId,
          element.name,
          element.type,
          element.step,
          element.position,
          element.configuration,
        ],
      );

      return {
        id: res.rows[0].id,
        questionnaireId: res.rows[0].questionnaire_id,
        name: res.rows[0].name,
        type: res.rows[0].type,
        step: res.rows[0].step,
        position: res.rows[0].position,
        configuration: res.rows[0].configuration,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async updateESMElement(
    element: ExperienceSamplingElement,
  ): Promise<ExperienceSamplingElement> {
    try {
      const updated = await this.pool.query(
        'UPDATE esm_elements SET type = $1, step = $2, position = $3, configuration = $4, name = $5 WHERE id = $6 RETURNING *',
        [
          element.type,
          element.step,
          element.position,
          element.configuration,
          element.name,
          element.id,
        ],
      );

      return {
        id: updated.rows[0].id,
        questionnaireId: updated.rows[0].questionnaire_id,
        name: updated.rows[0].name,
        type: updated.rows[0].type,
        step: updated.rows[0].step,
        position: updated.rows[0].position,
        configuration: updated.rows[0].configuration,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async deleteESMElement(id: number): Promise<void> {
    try {
      await this.pool.query('DELETE FROM esm_elements WHERE id = $1', [id]);
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }
}
