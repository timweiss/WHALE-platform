import { Pool } from 'pg';
import { NotImplementedError } from '../config/errors';

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

  getStudyById(id: number): Promise<Study>;

  getStudyByEnrolmentKey(enrolmentKey: string): Promise<Study>;

  createEnrolment(
    enrolment: Pick<Enrolment, 'studyId' | 'participantId'>,
  ): Promise<Enrolment>;

  getEnrolmentByParticipantId(participantId: string): Promise<Enrolment>;

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
  ): Promise<File>;
}

export class Repository implements IRepository {
  private pool: Pool;

  constructor(pool: Pool) {
    this.pool = pool;
  }

  getStudyByEnrolmentKey(enrolmentKey: string): Promise<Study> {
    throw new Error('Method not implemented.');
  }

  createFile(
    readingId: number,
    file: Pick<UploadFile, 'filename' | 'path'>,
  ): Promise<File> {
    throw new NotImplementedError();
  }

  createSensorReading(
    enrolmentId: number,
    reading: Pick<SensorReading, 'sensorType' | 'data'>,
  ): Promise<SensorReading> {
    throw new NotImplementedError();
  }

  createSensorReadingBatched(
    enrolmentId: number,
    readings: Pick<SensorReading, 'sensorType' | 'data'>[],
  ): Promise<SensorReading[]> {
    throw new NotImplementedError();
  }

  createStudy(study: Pick<Study, 'name' | 'enrolmentKey'>): Promise<Study> {
    throw new NotImplementedError();
  }

  createEnrolment(
    enrolment: Pick<Enrolment, 'studyId' | 'participantId'>,
  ): Promise<Enrolment> {
    throw new Error('Method not implemented.');
  }

  getEnrolmentByParticipantId(participantId: string): Promise<Enrolment> {
    throw new NotImplementedError();
  }

  getStudies(): Promise<Study[]> {
    throw new NotImplementedError();
  }

  getStudyById(id: number): Promise<Study> {
    throw new NotImplementedError();
  }
}
