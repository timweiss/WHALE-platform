// Must be the first import of every entry point (index.ts, worker.ts): the
// auto-instrumentations can only patch http, express, pg and ioredis if they
// are registered before those modules are loaded.
import { NodeSDK } from '@opentelemetry/sdk-node';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';

// Only runs where the deployment sets an OTLP endpoint (staging), so
// production and the tests start no SDK and export nothing.
//
// Metrics only. Logs are written to stdout and collected from there; traces
// have no backend. The metric exporter, service name and resource attributes
// come from the OTEL_* environment variables.
export const sdk = process.env.OTEL_EXPORTER_OTLP_ENDPOINT
  ? new NodeSDK({
      spanProcessors: [],
      logRecordProcessors: [],
      instrumentations: [getNodeAutoInstrumentations()],
    })
  : undefined;

sdk?.start();
