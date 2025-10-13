import { z } from 'zod';

export const ClientSensorReading = z.object({
  sensorType: z.string(),
  data: z.string(),
  timestamp: z.string(),
  localId: z.uuid(),
});