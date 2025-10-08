import * as z from 'zod';

export enum NotificationTriggerStatus {
  Planned = 'Planned',
  Pushed = 'Pushed',
  Displayed = 'Displayed',
  Answered = 'Answered',
}

export enum NotificationTriggerPriority {
  Default = 'Default',
  WaveBreaking = 'WaveBreaking',
}

export enum NotificationTriggerModality {
  EventContingent = 'EventContingent',
  Push = 'Push',
}

export enum NotificationTriggerSource {
  Scheduled = 'Scheduled',
  RuleBased = 'RuleBased',
}

export const NotificationTriggerValidation = z.object({
  uid: z.uuid(),
  addedAt: z.number(),
  name: z.string(),
  status: z.enum(NotificationTriggerStatus),
  validFrom: z.number(),
  priority: z.enum(NotificationTriggerPriority),
  modality: z.enum(NotificationTriggerModality),
  source: z.enum(NotificationTriggerSource),
  questionnaireId: z.number(),
  triggerId: z.number(),
  plannedAt: z.number().nullable(),
  pushedAt: z.number().nullable(),
  displayedAt: z.number().nullable(),
  answeredAt: z.number().nullable(),
  updatedAt: z.number(),
});

export type NotificationTrigger = z.infer<typeof NotificationTriggerValidation>;
