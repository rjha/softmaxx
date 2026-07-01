package online.softmaxx.xapi.kafka;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.CloseOptions;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;


public final class TubeWorker implements Runnable {

    private static final System.Logger LOGGER = System.getLogger("TubeWorker");
    
    private final String workerId;
    private final String topic;
    private final KafkaConsumer<String, String> consumer;
    private final AtomicBoolean shutdown;
    private static final String RECONNECT_BACKOFF_MS = "5000";
    private static final String RECONNECT_BACKOFF_MAX_MS = "30000";


    public TubeWorker(final String workerId, final String topic, final String groupId, final String bootstrapServers) {
        
        this.workerId = workerId;
        this.topic = topic;
        this.shutdown = new AtomicBoolean(false);
        final Properties props = new Properties();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        // Next-gen KIP-848 protocol optimization
        props.put("group.protocol", "consumer"); 
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, RECONNECT_BACKOFF_MS);
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, RECONNECT_BACKOFF_MAX_MS);
        this.consumer = new KafkaConsumer<>(props);
    }


    @Override
    public void run() {

        try {

            this.consumer.subscribe(Collections.singletonList(this.topic));
            LOGGER.log(System.Logger.Level.INFO, "[{0}] Subscribed and polling topic: {1}", workerId, topic);

            while (!shutdown.get()) {

                try {

                    // poll the broker for new records 
                    final ConsumerRecords<String, String> records = this.consumer.poll(Duration.ofSeconds(1));
                    processRecords(records);
                    doCommitSync();

                } catch (final WakeupException e) {
                    // 3. Captures wakeups from BOTH poll() and the rethrow from doCommitSync()
                    if (shutdown.get()) {
                        LOGGER.log(System.Logger.Level.INFO, "[{0}] Shutting down loop via wakeup.", workerId);
                        break;
                    }
                    LOGGER.log(System.Logger.Level.WARNING, "[{0}] Spurious wakeup detected, resuming poll...", workerId);
                } catch (final RetriableException networkEx) {
                    LOGGER.log(System.Logger.Level.WARNING, "[{0}] Retriable network error, retrying poll...", workerId, networkEx);
                    
                }
            }

        } catch (final Exception ex) {
            LOGGER.log(System.Logger.Level.ERROR, "[" + workerId + "] processing thread error", ex);

        } finally {

            LOGGER.log(System.Logger.Level.INFO, "[{0}] kafka consumer close, release resources...", workerId);
            final CloseOptions closeOptions = CloseOptions
                .groupMembershipOperation(CloseOptions.GroupMembershipOperation.DEFAULT)
                .withTimeout(Duration.ofSeconds(5));
            
            this.consumer.close(closeOptions);

        }
    }

    public void shutdown() {
        LOGGER.log(System.Logger.Level.INFO, "[{0}] worker thread shutdown...", workerId);
        this.shutdown.set(true);
        this.consumer.wakeup(); 
    }

    private void processRecords( final ConsumerRecords<String, String> records) {

        for (final ConsumerRecord<String, String> record : records) {
            try {

                final String key = record.key() != null ? record.key() : "NoKey";
                final String payload = record.value();
                LOGGER.log(System.Logger.Level.INFO, "[{0}] record -> Key: {1} | Payload: {2}", workerId, key, payload);
                //  do something with the payload

            } catch (final Exception ex) {
                LOGGER.log(System.Logger.Level.ERROR,  "[{0}] fatal: handler failed for record at offset: {1}", workerId, record.offset(),  ex);
            }
        }
    }

    private void doCommitSync() {
        try {
            // Perform the standard blocking commit
            this.consumer.commitSync();
        } catch (final WakeupException e) {
            // We were woken up during the commit. 
            // We will try one final time with a strict timeout to avoid blocking indefinitely,
            // then rethrow the exception so the main loop knows a shutdown/wakeup was requested.
            try {
                LOGGER.log(System.Logger.Level.INFO, "Commit interrupted by wakeup. Retrying final commit...");
                this.consumer.commitSync(Duration.ofSeconds(2));
            } catch (Exception finalEx) {
                LOGGER.log(System.Logger.Level.ERROR, "Final fallback commit failed", finalEx);
            }
            throw e; 
        } catch (final CommitFailedException e) {
            // The commit failed unrecoverably (e.g., rebalance occurred).
            // Safe to log and move on since another worker now owns these partitions.
            LOGGER.log(System.Logger.Level.ERROR, "Commit failed unrecoverably", e);
        }
    }

   
}
