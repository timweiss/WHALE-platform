/* eslint-disable */
import { NodeSDK } from "@opentelemetry/sdk-node";
import { getNodeAutoInstrumentations } from "@opentelemetry/auto-instrumentations-node";
import {
  MeterProvider,
  PeriodicExportingMetricReader,
} from "@opentelemetry/sdk-metrics";

import { OTLPTraceExporter } from "@opentelemetry/exporter-trace-otlp-proto";
import { OTLPMetricExporter } from "@opentelemetry/exporter-metrics-otlp-proto";

import {
  LoggerProvider,
  SimpleLogRecordProcessor,
} from "@opentelemetry/sdk-logs";
import { OTLPLogExporter } from "@opentelemetry/exporter-logs-otlp-proto";
import { resourceFromAttributes } from "@opentelemetry/resources";
import opentelemetry, { Meter, Tracer } from "@opentelemetry/api";

// Run docker run -p 8080:8080 -p 4317:4317 -p 4318:4318 docker.hyperdx.io/hyperdx/hyperdx-all-in-one
// for a local collector (read more at https://clickhouse.com/docs/use-cases/observability/clickstack/getting-started)

// For troubleshooting, set the log level to DiagLogLevel.DEBUG
// import { diag, DiagConsoleLogger, DiagLogLevel } from "@opentelemetry/api";
// diag.setLogger(new DiagConsoleLogger(), DiagLogLevel.INFO);

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
  const metricExporter = new OTLPMetricExporter();
  const traceExporter = new OTLPTraceExporter();
  const logsExporter = new OTLPLogExporter();

  // Export traces and metrics
  const sdk = new NodeSDK({
    // traceExporter: new ConsoleSpanExporter(),
    // metricReader: new PeriodicExportingMetricReader({
    //   exporter: new ConsoleMetricExporter(),
    // }),

    traceExporter,
    metricReader: new PeriodicExportingMetricReader({
      exporter: metricExporter,
    }),

    logRecordProcessors: [new SimpleLogRecordProcessor(logsExporter)],

    instrumentations: [getNodeAutoInstrumentations()],
  });
  sdk.start();

  // Export metrics
  const metricReader = new PeriodicExportingMetricReader({
    exporter: metricExporter,
  });

  const meterProvider = new MeterProvider({
    resource: resourceFromAttributes({ "service.name": "whale" }),
    readers: [metricReader],
  });
  opentelemetry.metrics.setGlobalMeterProvider(meterProvider);

  // Export logs
  const logProvider = new LoggerProvider({
    resource: resourceFromAttributes({ "service.name": "whale" }),
    processors: [new SimpleLogRecordProcessor(logsExporter)],
  });

  const logger = logProvider.getLogger("whale");

  const createLogger = (
    name: string,
    loggerAttributes?: Record<string, any>
  ): Logger & LoggerWithAttributes => {
    const log = (
      level: string,
      message: string,
      attributes?: Record<string, any>
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

      logger.emit({
        timestamp: new Date(),
        severityText: level,
        body: message,
        attributes: {
          ...loggerAttributes,
          ...attributes,
        },
      });
    };

    return {
      debug: (message, attributes) =>
        log("DEBUG", message, { ...attributes, name }),
      info: (message, attributes) =>
        log("INFO", message, { ...attributes, name }),
      warn: (message, attributes) =>
        log("WARN", message, { ...attributes, name }),
      error: (message, attributes) =>
        log("ERROR", message, { ...attributes, name }),
      withAttributes: (attributes) => createLogger(name, attributes),
    };
  };

  const tracer = opentelemetry.trace.getTracer("learnlm_whale");

  return {
    logger: createLogger("root"),
    onShutdown: async () => {
      await sdk.shutdown();
      await logProvider.shutdown();
    },
    tracer,
  };
}

export function getMeter(scope: string, version?: string): Meter {
  return opentelemetry.metrics.getMeter(scope, version);
}

function metricsPrefix(name: string) {
  return `learnlm_whale.${name}`;
}

export function HistogramLaunchBrowser(duration: number) {
  const meter = getMeter("whale");

  const histogram = meter.createHistogram(
    metricsPrefix("browser_launch_duration"),
    {
      unit: "ms",
      description: "Duration of browser launch",
    }
  );

  histogram.record(duration);
}

export function HistogramSensorUpload(duration: number) {
  const meter = getMeter("whale");

  const histogram = meter.createHistogram(
    metricsPrefix("sensor_batch_upload_duration"),
    {
      unit: "ms",
      description: "Duration of sensor data upload",
    }
  );

  histogram.record(duration);
}

export async function withDuration<T>(
  fn: () => Promise<T>,
  durationFn: (duration: number) => void
): Promise<T> {
  const start = Date.now();
  const result = await fn();
  const duration = Date.now() - start;
  durationFn(duration);
  return result;
}
