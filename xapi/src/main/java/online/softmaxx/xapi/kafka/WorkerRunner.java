package online.softmaxx.xapi.kafka;

import java.util.concurrent.CountDownLatch;


public final class WorkerRunner {


    private static final System.Logger LOGGER = System.getLogger(WorkerRunner.class.getName());

    public static void main(final String[] args) {
        
        LOGGER.log(System.Logger.Level.INFO, "Starting single-worker testing context...");

        // Consolidated variables for straightforward local validation
        final String workerId = "test-worker-01";
        final String targetTopic = "xapi_tube";
        final String consumerGroupId = "xapi_tube_test_group";
        final String bootstrapServers = "localhost:9092";

        final CountDownLatch shutdownLatch = new CountDownLatch(1);

        // 1. Initialize the worker (All setup logic fires inside this constructor)
        final TubeWorker tubeWorker = new TubeWorker(
            workerId, 
            targetTopic, 
            consumerGroupId, 
            bootstrapServers
        );

        // 2. Spawn exactly one isolated testing instance on a Virtual Thread
        LOGGER.log(System.Logger.Level.INFO, "Spawning worker [{0}] on JDK Virtual Thread...", workerId);
        Thread.startVirtualThread(tubeWorker);

        // 3. Register a clean OS shutdown interceptor hook (SIGINT/SIGTERM)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.log(System.Logger.Level.INFO, "Shutdown event captured. Tearing down worker safely...");
            tubeWorker.shutdown(); // Safe, warning-free CloseOptions teardown
            shutdownLatch.countDown();
        }));

        // 4. Block the main execution thread context until the shutdown hook unlocks it
        try {
            shutdownLatch.await();
            LOGGER.log(System.Logger.Level.INFO, "Worker process exited cleanly.");
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
