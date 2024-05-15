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
}

export interface UploadFile {
  id: number;
  readingId: number;
  filename: string;
  path: string;
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
    reading: Pick<SensorReading, 'sensorType' | 'data'>,
  ): Promise<SensorReading> {
    try {
      const res = await this.pool.query(
        'INSERT INTO sensor_readings (enrolment_id, sensor_type, data) VALUES ($1, $2, $3) RETURNING *',
        [enrolmentId, reading.sensorType, reading.data],
      );
      return {
        id: res.rows[0].id,
        enrolmentId: res.rows[0].enrolment_id,
        sensorType: res.rows[0].sensor_type,
        data: res.rows[0].data,
      };
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async createSensorReadingBatched(
    enrolmentId: number,
    readings: Pick<SensorReading, 'sensorType' | 'data'>[],
  ): Promise<SensorReading[]> {
    try {
      const batched = await Promise.all(
        readings.map((reading) =>
          this.pool.query(
            'INSERT INTO sensor_readings (enrolment_id, sensor_type, data) VALUES ($1, $2, $3) RETURNING *',
            [enrolmentId, reading.sensorType, reading.data],
          ),
        ),
      );

      return batched.map((res) => ({
        id: res.rows[0].id,
        enrolmentId: res.rows[0].enrolment_id,
        sensorType: res.rows[0].sensor_type,
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
}
