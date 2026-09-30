/* eslint-disable */
import { trace, Tracer } from '@opentelemetry/api';
import { sdk } from './instrumentation';

// Logs are JSON lines on stdout: Docker hands them to the journal, and the
// monitoring agent ships them to Loki with their level. Metrics are set up in
// instrumentation.ts.

export interface Logger {
  debug(message: string, attributes?: Record<string, any>): void;
  info(message: string, attributes?: Record<string, any>): void;
  warn(message: string, attributes?: Record<string, any>): void;
  error(message: string, attributes?: Record<string, any>): void;
}

export interface LoggerWithAttributes {
  withAttributes(attributes: Record<string, any>): Logger;
}

export interface Observability {
  logger: Logger;
  tracer: Tracer;
}

export async function setupO11y(): Promise<{
  logger: Logger;
  onShutdown: () => Promise<void>;
  tracer: Tracer;
}> {
  const createLogger = (
    name: string,
    loggerAttributes?: Record<string, any>,
  ): Logger & LoggerWithAttributes => {
    const log = (
      level: string,
      message: string,
      attributes?: Record<string, any>,
    ) => {
      const timestamp = new Date().toISOString();
      const logData = {
        timestamp,
        level,
        name,
        message,
        ...loggerAttributes,
        ...attributes,
      };

      // Output structured logs to console for Docker logs
      // Use JSON format for easier parsing and log aggregation
      console.log(JSON.stringify(logData));
    };

    return {
      debug: (message, attributes) =>
        log('DEBUG', message, { ...attributes, name }),
      info: (message, attributes) =>
        log('INFO', message, { ...attributes, name }),
      warn: (message, attributes) =>
        log('WARN', message, { ...attributes, name }),
      error: (message, attributes) =>
        log('ERROR', message, { ...attributes, name }),
      withAttributes: (attributes) => createLogger(name, attributes),
    };
  };

  return {
    logger: createLogger('root'),
    onShutdown: async () => {
      await sdk?.shutdown();
    },
    // No-op unless a tracer provider is registered (none is today).
    tracer: trace.getTracer('whale'),
  };
}
