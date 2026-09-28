import { z } from 'zod';

export const ClientSensorReading = z.object({
  sensorType: z.string().max(255),
  data: z.string(),
  timestamp: z.number(),
  localId: z.uuid(),
});