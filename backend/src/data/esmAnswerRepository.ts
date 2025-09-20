import { Repository } from './repository';
import { DatabaseError } from '../config/errors';
import {
  NotificationTrigger,
  NotificationTriggerModality,
  NotificationTriggerPriority,
  NotificationTriggerSource,
  NotificationTriggerStatus,
} from '../model/notification-trigger';

export enum ExperienceSamplingAnswerStatus {
  Notified = 'notified',
  Pending = 'pending',
  Completed = 'completed',
}

export interface ExperienceSamplingAnswer {
  id: number;
  enrolmentId: string;
  questionnaireId: number;
  answers: string;
  pendingQuestionnaireId: string;
  status: ExperienceSamplingAnswerStatus;
  lastOpenedPage: number;
  createdTimestamp: number;
  lastUpdatedTimestamp: number;
  finishedTimestamp: number | null;
  notificationTriggerId: string | null;
}

interface ExperienceSamplingAnswerRow {
  id: number;
  enrolment_id: string;
  questionnaire_id: number;
  answers: string;
  pending_questionnaire_id: string;
  status: ExperienceSamplingAnswerStatus;
  last_opened_page: number;
  created_timestamp: number;
  last_updated_timestamp: number;
  finished_timestamp: number | null;
  notification_trigger_id: string | null;
}

interface NotificationTriggerRow {
  local_id: string;
  enrolment_id: number;
  trigger_id: number;
  questionnaire_id: number;
  added_at: number;
  name: string;
  status: string;
  valid_from: number;
  priority: string;
  modality: string;
  source: string;
  source_notification_trigger_id: string | null;
  planned_at: number | null;
  pushed_at: number | null;
  displayed_at: number | null;
  answered_at: number | null;
  updated_at: number;
}

export interface IESMAnswerRepository {
  createESMAnswer(
    answer: Pick<
      ExperienceSamplingAnswer,
      | 'enrolmentId'
      | 'questionnaireId'
      | 'answers'
      | 'pendingQuestionnaireId'
      | 'lastOpenedPage'
      | 'status'
      | 'createdTimestamp'
      | 'lastUpdatedTimestamp'
      | 'finishedTimestamp'
      | 'notificationTriggerId'
    >,
  ): Promise<ExperienceSamplingAnswer>;

  getESMAnswerForPendingQuestionnaireId(
    pendingQuestionnaireId: string,
  ): Promise<ExperienceSamplingAnswer | null>;

  createNotificationTrigger(
    enrolmentId: number,
    notificationTrigger: NotificationTrigger,
  ): Promise<NotificationTrigger | null>;

  getNotificationTriggerByLocalId(
    enrolmentId: number,
    localId: string,
  ): Promise<NotificationTrigger | null>;

  updateNotificationTrigger(
    enrolmentId: number,
    notificationTrigger: NotificationTrigger,
  ): Promise<NotificationTrigger | null>;
}

export class ESMResponseRepository
  extends Repository
  implements IESMAnswerRepository
{
  private answerFromRow(
    row: ExperienceSamplingAnswerRow,
  ): ExperienceSamplingAnswer {
    return {
      id: row.id,
      enrolmentId: row.enrolment_id,
      questionnaireId: row.questionnaire_id,
      answers: row.answers,
      pendingQuestionnaireId: row.pending_questionnaire_id,
      createdTimestamp: row.created_timestamp,
      finishedTimestamp: row.finished_timestamp,
      lastUpdatedTimestamp: row.last_updated_timestamp,
      lastOpenedPage: row.last_opened_page,
      status: row.status as ExperienceSamplingAnswerStatus,
      notificationTriggerId: row.notification_trigger_id,
    };
  }

  private notificationTriggerFromRow(
    row: NotificationTriggerRow,
  ): NotificationTrigger {
    return {
      uid: row.local_id,
      addedAt: row.added_at,
      name: row.name,
      status: row.status as NotificationTriggerStatus,
      validFrom: row.valid_from,
      priority: row.priority as NotificationTriggerPriority,
      modality: row.modality as NotificationTriggerModality,
      source: row.source as NotificationTriggerSource,
      sourceNotificationTriggerId: row.source_notification_trigger_id,
      questionnaireId: row.questionnaire_id,
      triggerId: row.trigger_id,
      plannedAt: row.planned_at,
      pushedAt: row.pushed_at,
      displayedAt: row.displayed_at,
      answeredAt: row.answered_at,
      updatedAt: row.updated_at,
    };
  }

  async createESMAnswer(
    answer: Pick<
      ExperienceSamplingAnswer,
      | 'answers'
      | 'enrolmentId'
      | 'questionnaireId'
      | 'pendingQuestionnaireId'
      | 'lastOpenedPage'
      | 'status'
      | 'createdTimestamp'
      | 'lastUpdatedTimestamp'
      | 'finishedTimestamp'
      | 'notificationTriggerId'
    >,
  ): Promise<ExperienceSamplingAnswer> {
    try {
      const res = await this.pool.query(
        'INSERT INTO esm_answers (enrolment_id, questionnaire_id, answers, pending_questionnaire_id, created_timestamp, last_updated_timestamp, finished_timestamp, last_opened_page, status, notification_trigger_id) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10) RETURNING *',
        [
          answer.enrolmentId,
          answer.questionnaireId,
          answer.answers,
          answer.pendingQuestionnaireId,
          answer.createdTimestamp,
          answer.lastUpdatedTimestamp,
          answer.finishedTimestamp,
          answer.lastOpenedPage,
          answer.status,
          answer.notificationTriggerId,
        ],
      );

      return this.answerFromRow(res.rows[0]);
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getESMAnswerForPendingQuestionnaireId(
    pendingQuestionnaireId: string,
  ): Promise<ExperienceSamplingAnswer | null> {
    try {
      const res = await this.pool.query(
        'SELECT * FROM esm_answers WHERE pending_questionnaire_id = $1',
        [pendingQuestionnaireId],
      );

      if (res.rows.length === 0) {
        return null;
      }

      return this.answerFromRow(res.rows[0]);
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async createNotificationTrigger(
    enrolmentId: number,
    notificationTrigger: NotificationTrigger,
  ): Promise<NotificationTrigger | null> {
    try {
      const res = await this.pool.query(
        'INSERT INTO esm_notification_trigger (local_id, enrolment_id, trigger_id, questionnaire_id, added_at, name, status, valid_from, priority, modality, source, source_notification_trigger_id, planned_at, pushed_at, displayed_at, answered_at, updated_at) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17) RETURNING *',
        [
          notificationTrigger.uid,
          enrolmentId,
          notificationTrigger.triggerId,
          notificationTrigger.questionnaireId,
          notificationTrigger.addedAt,
          notificationTrigger.name,
          notificationTrigger.status,
          notificationTrigger.validFrom,
          notificationTrigger.priority,
          notificationTrigger.modality,
          notificationTrigger.source,
          notificationTrigger.sourceNotificationTriggerId,
          notificationTrigger.plannedAt,
          notificationTrigger.pushedAt,
          notificationTrigger.displayedAt,
          notificationTrigger.answeredAt,
          notificationTrigger.updatedAt,
        ],
      );

      if (res.rows.length === 0) {
        return null;
      }

      return this.notificationTriggerFromRow(res.rows[0]);
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async getNotificationTriggerByLocalId(
    enrolmentId: number,
    localId: string,
  ): Promise<NotificationTrigger | null> {
    try {
      const res = await this.pool.query(
        'SELECT * FROM esm_notification_trigger WHERE enrolment_id = $1 AND local_id = $2',
        [enrolmentId, localId],
      );

      if (res.rows.length === 0) {
        return null;
      }

      return this.notificationTriggerFromRow(res.rows[0]);
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }

  async updateNotificationTrigger(
    enrolmentId: number,
    notificationTrigger: NotificationTrigger,
  ): Promise<NotificationTrigger | null> {
    try {
      const res = await this.pool.query(
        'UPDATE esm_notification_trigger SET trigger_id = $3, questionnaire_id = $4, added_at = $5, name = $6, status = $7, valid_from = $8, priority = $9, modality = $10, source = $11, source_notification_trigger_id = $12, planned_at = $13, pushed_at = $14, displayed_at = $15, answered_at = $16, updated_at = $17 WHERE enrolment_id = $1 AND local_id = $2 RETURNING *',
        [
          enrolmentId,
          notificationTrigger.uid,
          notificationTrigger.triggerId,
          notificationTrigger.questionnaireId,
          notificationTrigger.addedAt,
          notificationTrigger.name,
          notificationTrigger.status,
          notificationTrigger.validFrom,
          notificationTrigger.priority,
          notificationTrigger.modality,
          notificationTrigger.source,
          notificationTrigger.sourceNotificationTriggerId,
          notificationTrigger.plannedAt,
          notificationTrigger.pushedAt,
          notificationTrigger.displayedAt,
          notificationTrigger.answeredAt,
          notificationTrigger.updatedAt,
        ],
      );

      if (res.rows.length === 0) {
        return null;
      }

      return this.notificationTriggerFromRow(res.rows[0]);
    } catch (e) {
      throw new DatabaseError((e as Error).message.toString());
    }
  }
}
