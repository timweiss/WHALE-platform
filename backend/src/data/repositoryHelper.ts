import { IStudyRepository, StudyRepository } from './studyRepository';
import {
  ExperienceSamplingRepository,
  IExperienceSamplingRepository,
} from './experienceSamplingRepository';
import {
  ISensorReadingRepository,
  SensorReadingRepository,
} from './sensorReadingRepository';
import {
  EnrolmentRepository,
  IEnrolmentRepository,
} from './enrolmentRepository';
import { Pool } from 'pg';

export interface Repositories {
  study: IStudyRepository;
  experienceSampling: IExperienceSamplingRepository;
  sensorReading: ISensorReadingRepository;
  enrolment: IEnrolmentRepository;
}

export function initializeRepositories(pool: Pool): Repositories {
  return {
    study: new StudyRepository(pool),
    experienceSampling: new ExperienceSamplingRepository(pool),
    sensorReading: new SensorReadingRepository(pool),
    enrolment: new EnrolmentRepository(pool),
  };
}
