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
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

public final class TubeWorker implements Runnable {

    private static final System.Logger LOGGER = System.getLogger("TubeWorker");
    
    private final String workerId;
    private final String topic;
    private final KafkaConsumer<String, String> consumer;
    private final AtomicBoolean keepRunning = new AtomicBoolean(true);

    public TubeWorker(final String workerId, final String topic, final String groupId, final String bootstrapServers) {
        
        this.workerId = workerId;
        this.topic = topic;

        final Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put("group.protocol", "consumer"); // Next-gen KIP-848 protocol optimization

        this.consumer = new KafkaConsumer<>(props);
    }

    @Override
    public void run() {

        try {

            this.consumer.subscribe(Collections.singletonList(this.topic));
            LOGGER.log(System.Logger.Level.INFO, "[{0}] Subscribed and polling topic: {1}", workerId, topic);

            while (this.keepRunning.get()) {
                final ConsumerRecords<String, String> records = this.consumer.poll(Duration.ofSeconds(1));
                for (final ConsumerRecord<String, String> record : records) {
                    final String key = record.key() != null ? record.key() : "NoKey";
                    final String payload = record.value();

                    LOGGER.log(System.Logger.Level.INFO, "[{0}] Processed -> Key: {1} | Payload: {2}", 
                            workerId, key, payload);
                }
            }
        } catch (final WakeupException e) {
            LOGGER.log(System.Logger.Level.DEBUG, "[{0}] Consumer wakeup initiated.", workerId);
        } catch (final Exception ex) {
            LOGGER.log(System.Logger.Level.ERROR, "[" + workerId + "] Error in processing thread", ex);
        } finally {

            LOGGER.log(System.Logger.Level.INFO, "[{0}] Releasing socket resources safely...", workerId);
            // 1. Initialize an options instance using a static factory method
            final CloseOptions closeOptions = CloseOptions
                .groupMembershipOperation(CloseOptions.GroupMembershipOperation.DEFAULT)
                .withTimeout(Duration.ofSeconds(5));
            
            this.consumer.close(closeOptions);

        }
    }

    public void stop() {
        LOGGER.log(System.Logger.Level.INFO, "[{0}] Requesting thread stop...", workerId);
        this.keepRunning.set(false);
        this.consumer.wakeup(); 
    }
}
