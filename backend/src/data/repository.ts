import { Pool } from 'pg';
import { DatabaseError } from '../config/errors';

export interface Study {
  id: number;
  name: string;
  enrolmentKey: string;
}

export interface Enrolment {
  id: number;
  studyId: number;
  participantId: string;
}

type SensorType = string;
type SensorData = string;

export interface SensorReading {
  id: number;
  enrolmentId: number;
  sensorType: SensorType;
  data: SensorData;
  timestamp: string;
}

export interface UploadFile {
  id: number;
  readingId: number;
  filename: string;
  path: string;
}

export interface ExperienceSamplingQuestionnaire {
  id: number;
  studyId: number;
  name: string;
  enabled: boolean;
  version: number;
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
  configuration: TriggerConfiguration;
  enabled: boolean;
}

export interface ExperienceSamplingAnswer {
  id: number;
  enrolmentId: string;
  questionnaireId: number;
  answers: string;
}

export interface IRepository {
  getStudies(): Promise<Study[]>;

  createStudy(study: Pick<Study, 'name' | 'enrolmentKey'>): Promise<Study>;

  getStudyById(id: number): Promise<Study | null>;

  getStudyByEnrolmentKey(enrolmentKey: string): Promise<Study | null>;

  createEnrolment(
    enrolment: Pick<Enrolment, 'studyId' | 'participantId'>,
  ): Promise<Enrolment>;

  getEnrolmentByParticipantId(participantId: string): Promise<Enrolment | null>;

  getEnrolmentById(id: number): Promise<Enrolment | null>;

  createSensorReading(
    enrolmentId: number,
    reading: Pick<SensorReading, 'sensorType' | 'data'>,
  ): Promise<SensorReading>;

  createSensorReadingBatched(
    enrolmentId: number,
    readings: Pick<SensorReading, 'sensorType' | 'data'>[],
  ): Promise<SensorReading[]>;

  createFile(
    readingId: number,
    file: Pick<UploadFile, 'filename' | 'path'>,
  ): Promise<UploadFile>;

  // Experience Sampling
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
      'questionnaireId' | 'type' | 'configuration' | 'enabled'
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
      'questionnaireId' | 'type' | 'step' | 'position' | 'configuration'
    >,
  ): Promise<ExperienceSamplingElement>;

  updateESMElement(
    element: ExperienceSamplingElement,
  ): Promise<ExperienceSamplingElement>;

  deleteESMElement(id: number): Promise<void>;

  createESMAnswer(
    answer: Pick<
      ExperienceSamplingAnswer,
      'enrolmentId' | 'questionnaireId' | 'answers'
    >,
  ): Promise<ExperienceSamplingAnswer>;
}

export class Repository implements IRepository {
  private pool: Pool;

  constructor(pool: Pool) {
    this.pool = pool;
  }

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
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

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
    reading: Pick<SensorReading, 'sensorType' | 'timestamp' | 'data'>,
  ): Promise<SensorReading> {
    try {
      const res = await this.pool.query(
        'INSERT INTO sensor_readings (enrolment_id, sensor_type, timestamp, data) VALUES ($1, $2, $3, $4) RETURNING *',
        [enrolmentId, reading.sensorType, reading.timestamp, reading.data],
      );
      return {
        id: res.rows[0].id,
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
    readings: Pick<SensorReading, 'sensorType' | 'timestamp' | 'data'>[],
  ): Promise<SensorReading[]> {
    try {
      const batched = await Promise.all(
        readings.map((reading) =>
          this.pool.query(
            'INSERT INTO sensor_readings (enrolment_id, sensor_type, timestamp, data) VALUES ($1, $2, $3, $4) RETURNING *',
            [enrolmentId, reading.sensorType, reading.timestamp, reading.data],
          ),
        ),
      );

      return batched.map((res) => ({
        id: res.rows[0].id,
        enrolmentId: res.rows[0].enrolment_id,
        sensorType: res.rows[0].sensor_type,
        timestamp: res.rows[0].timestamp,
        data: res.rows[0].data,
      }));
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async createStudy(
    study: Pick<Study, 'name' | 'enrolmentKey'>,
  ): Promise<Study> {
    try {
      const res = await this.pool.query(
        'INSERT INTO studies (name, enrolment_key) VALUES ($1, $2) RETURNING *',
        [study.name, study.enrolmentKey],
      );
      return {
        id: res.rows[0].id,
        name: res.rows[0].name,
        enrolmentKey: res.rows[0].enrolment_key,
      };
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
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  // Experience Sampling

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
        'UPDATE esm_questionnaires SET name = $1, enabled = $2 WHERE id = $3 RETURNING *',
        [questionnaire.name, questionnaire.enabled, questionnaire.id],
      );

      return {
        id: updated.rows[0].id,
        studyId: updated.rows[0].study_id,
        name: updated.rows[0].name,
        enabled: updated.rows[0].enabled,
        version: newVersion,
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
      'enabled' | 'questionnaireId' | 'type' | 'configuration'
    >,
  ): Promise<ExperienceSamplingTrigger> {
    try {
      const res = await this.pool.query(
        'INSERT INTO esm_triggers (questionnaire_id, type, configuration, enabled) VALUES ($1, $2, $3, $4) RETURNING *',
        [
          trigger.questionnaireId,
          trigger.type,
          trigger.configuration,
          trigger.enabled,
        ],
      );

      return {
        id: res.rows[0].id,
        questionnaireId: res.rows[0].questionnaire_id,
        type: res.rows[0].type,
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
        'UPDATE esm_triggers SET type = $1, configuration = $2, enabled = $3 WHERE id = $4 RETURNING *',
        [trigger.type, trigger.configuration, trigger.enabled, trigger.id],
      );

      return {
        id: updated.rows[0].id,
        questionnaireId: updated.rows[0].questionnaire_id,
        type: updated.rows[0].type,
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
      | 'configuration'
      | 'step'
      | 'position'
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

  async createESMAnswer(
    answer: Pick<
      ExperienceSamplingAnswer,
      'enrolmentId' | 'questionnaireId' | 'answers'
    >,
  ): Promise<ExperienceSamplingAnswer> {
    try {
      const res = await this.pool.query(
        'INSERT INTO esm_answers (enrolment_id, questionnaire_id, answers) VALUES ($2, $3, $4) RETURNING *',
        [answer.enrolmentId, answer.questionnaireId, answer.answers],
      );

      return {
        id: res.rows[0].id,
        enrolmentId: res.rows[0].enrolment_id,
        questionnaireId: res.rows[0].questionnaire_id,
        answers: res.rows[0].answers,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }
}
