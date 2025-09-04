import { IStudyRepository, StudyRepository } from './studyRepository';
import {
  ESMConfigRepository,
  IESMConfigRepository,
} from './esmConfigRepository';
import {
  ESMResponseRepository,
  IESMAnswerRepository,
} from './esmAnswerRepository';
import {
  ISensorReadingRepository,
  SensorReadingRepository,
} from './sensorReadingRepository';
import {
  EnrolmentRepository,
  IEnrolmentRepository,
} from './enrolmentRepository';
import { Pool } from 'pg';
import {
  CompletionRepository,
  ICompletionRepository,
} from './completionRepository';
import { Observability } from '../o11y';

export interface Repositories {
  study: IStudyRepository;
  esmConfig: IESMConfigRepository;
  esmAnswer: IESMAnswerRepository;
  sensorReading: ISensorReadingRepository;
  enrolment: IEnrolmentRepository;
  completion: ICompletionRepository;
}

export function initializeRepositories(
  pool: Pool,
  observability: Observability,
): Repositories {
  return {
    study: new StudyRepository(pool, observability),
    esmConfig: new ESMConfigRepository(pool, observability),
    esmAnswer: new ESMResponseRepository(pool, observability),
    sensorReading: new SensorReadingRepository(pool, observability),
    enrolment: new EnrolmentRepository(pool, observability),
    completion: new CompletionRepository(pool, observability),
  };
}
