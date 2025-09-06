import { z } from 'zod';

// Logical operators for combining conditions
const LogicalOperatorSchema = z.enum(['and', 'or']);

// Comparison operators
const ComparatorSchema = z.enum(['equals', 'not_equals']);

// Individual condition schema
const ConditionSchema = z.object({
  fieldName: z.string().min(1, 'Field name cannot be empty'),
  comparator: ComparatorSchema,
  expectedValue: z
    .union([z.string(), z.number(), z.boolean(), z.null(), z.array(z.any())])
    .describe('The value to compare against - can be any JSON type'),
});

const ConditionGroupSchema = z.object({
  operator: LogicalOperatorSchema.default('and'),
  conditions: z
    .array(ConditionSchema)
    .min(1, 'At least one condition is required'),
});

const ActionSchema = z.discriminatedUnion('type', [
  z.object({
    type: z.literal('open_questionnaire'),
    eventQuestionnaireTriggerId: z.number(),
  }),

  z.object({
    type: z.literal('put_notification_trigger'),
    triggerId: z.number(),
  }),
]);

const RuleSchema = z.object({
  name: z.string().min(1, 'Rule name cannot be empty'),
  conditions: ConditionGroupSchema,
  actions: z.array(ActionSchema).min(1, 'At least one action is required'),
});

export {
  RuleSchema,
  ConditionGroupSchema,
  ConditionSchema,
  ActionSchema,
  LogicalOperatorSchema,
  ComparatorSchema,
};

export type Rule = z.infer<typeof RuleSchema>;
export type ConditionGroup = z.infer<typeof ConditionGroupSchema>;
export type Condition = z.infer<typeof ConditionSchema>;
export type Action = z.infer<typeof ActionSchema>;
export type OpenQuestionnaireAction = Extract<Action, { type: 'open' }>;
export type PutNotificationTriggerAction = Extract<Action, { type: 'notify' }>;
export type LogicalOperator = z.infer<typeof LogicalOperatorSchema>;
export type Comparator = z.infer<typeof ComparatorSchema>;
