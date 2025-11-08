import { usePool } from './config/database';
import { setupO11y } from './o11y';
import { initializeRepositories } from './data/repositoryHelper';
import { createSensorReadingWorker } from './queues/sensorReadingQueue';

/**
 * Standalone worker process for processing sensor reading jobs from the queue.
 * This should be run as a separate process from the API server.
 */
async function main() {
  const olly = await setupO11y();

  olly.logger.info('Starting sensor reading worker');

  const pool = usePool(olly);
  const repositories = initializeRepositories(pool, olly);

  // Create and start the worker
  const worker = createSensorReadingWorker(
    repositories.sensorReading,
    olly,
  );

  olly.logger.info('Sensor reading worker started and waiting for jobs');

  // Graceful shutdown handling
  const shutdown = async (signal: string) => {
    olly.logger.info(`Received ${signal}, shutting down gracefully`);

    // Close the worker (this will finish processing current jobs)
    await worker.close();
    olly.logger.info('Worker closed');

    // Close database pool
    await pool.end();
    olly.logger.info('Database pool closed');

    // Shutdown observability
    await olly.onShutdown();

    process.exit(0);
  };

  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT', () => shutdown('SIGINT'));
}

// Only run when this file is executed directly
if (require.main === module) {
  main().catch((err) => {
    console.error('Worker failed to start:', err);
    process.exit(1);
  });
}

export { main };
