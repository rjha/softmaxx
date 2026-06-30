package online.softmaxx.xapi.kafka;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.CloseOptions;
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
    private final KafkaState KAFKA_STATE = new KafkaState();
   
    private static final long MIN_BACKOFF_MS = 2000L;   
    private static final long MAX_BACKOFF_MS = 60000L;  
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
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

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
                    // should we continue processing or do a wait + poll()? 
                    if(KAFKA_STATE.pollError(records.isEmpty(), this.consumer.assignment().isEmpty(), this)) {
                        continue; 
                    }
                    
                    processRecords(records);

                } catch (final RetriableException networkEx) {
                    // retriable-error, wait and try again
                    KAFKA_STATE.networkError(this);
                }
            }

        } catch (final WakeupException e) {
            // ignore - we are closing 
            LOGGER.log(System.Logger.Level.ERROR, "[{0}]kafka consumer wakeup ignored...", workerId);
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
                // CRITICAL BOUNDARY: A data payload or DB error must NEVER crash your network state machine loop.
                LOGGER.log(System.Logger.Level.ERROR,  "[" + workerId + "] fatal error in record processing", ex);
            }
        }
    }

    private void executeBackoffSleep() {

       try {
            // virtual thread sleep 
            Thread.sleep(KAFKA_STATE.currentBackoffMs);
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
            this.shutdown.set(true);
        } finally {
            KAFKA_STATE.incrementBackoff();
        }

    }

    private static final class KafkaState {

        private final int OK_STATE = 1;
        private final int ERROR_STATE = 2;
        private final int EMPTY_STATE = 3;

        private volatile int currentState;
        private volatile long currentBackoffMs;

        private KafkaState() {
            this.currentState = OK_STATE;
            this.currentBackoffMs = MIN_BACKOFF_MS;
        }

        private boolean pollError(boolean isRecordEmpty, boolean isAssignmentEmpty, TubeWorker worker) {
            
            this.emptyRecords(isRecordEmpty);
            this.consumerAssignment(isAssignmentEmpty);

            if (this.currentState == ERROR_STATE) {
                // indicates bad poll() result
                // skip further processing 
                worker.executeBackoffSleep();
                return true; 
            }

            return false;

        }

        private void networkError(TubeWorker worker) {
            this.currentState = ERROR_STATE;
            // worker.executeBackoffSleep();
        }

        // events 
        private void emptyRecords(boolean emptyFlag) {

            if(emptyFlag) {
                // No records. 
                // No change in EMPTY or ERROR state
                if (this.currentState == OK_STATE) {
                    this.currentState = EMPTY_STATE;
                }
                
            } else {
                // we found records. 
                this.currentState = OK_STATE;
                this.currentBackoffMs = MIN_BACKOFF_MS; 
            }

        }

        private void consumerAssignment(boolean errorFlag) {

            if(!errorFlag) {
                this.currentState = OK_STATE;
                this.currentBackoffMs = MIN_BACKOFF_MS;
            } else {
                // partition assigment error
                this.currentState = ERROR_STATE;
            }

        }

        private void incrementBackoff() {
            currentBackoffMs = Math.min(currentBackoffMs * 2, MAX_BACKOFF_MS);
        }
        
    }
}
