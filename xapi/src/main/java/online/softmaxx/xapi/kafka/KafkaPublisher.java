package online.softmaxx.xapi.kafka;


import java.util.Properties;
import java.time.Duration;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import online.softmaxx.xapi.util.LogTracker;


public final class KafkaPublisher {

    private static final System.Logger LOGGER = System.getLogger(KafkaPublisher.class.getName());
    private static final Provider PROVIDER = new Provider();
    private static final LogTracker LOG_TRACKER = new LogTracker(LOGGER);


    private KafkaPublisher() {
        throw new UnsupportedOperationException("KafkaPublisher class cannot be instantiated");
    }
    
    public static void send(final String topic, final String key, final String value) {

        final ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        
        // Let Kafka's internal background thread handle the async delivery 
        // and catch the error right when it happens. We cannot bubble up 
        // these errors to KafkaProxy
        PROVIDER.instance().send(record, (metadata, exception) -> {
            if (exception == null) {
                LOG_TRACKER.reset();
                LOGGER.log(System.Logger.Level.INFO,  "Kafka message saved to: " + stringify(metadata));
            } else {
                // send failed
                LOG_TRACKER.error("Kafka delivery failed for message: " + stringify(record), exception);
            }
        });
    }

    private static String stringify(RecordMetadata metadata) {
        return String.format("topic=%s, partition=%s, offset=%s", 
            metadata.topic(),  
            metadata.partition(), 
            metadata.offset());
    }

     private static String stringify(ProducerRecord<String, String> record) {
        return String.format("topic=%s, key=%s, value=%s", 
            record.topic(),  
            record.key(), 
            record.value());
    }

    private static final class Provider {

        private final KafkaProducer<String, String> instance;

        private Provider() {
            final Properties props = new Properties();
            
            // Core connectivity parameters
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            
            // Performance optimizations for high-throughput API microservices
            props.put(ProducerConfig.ACKS_CONFIG, "1");
            props.put(ProducerConfig.LINGER_MS_CONFIG, "5");

            // fail fast settings for producer
            props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "1500");
            props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "2000");
            props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "4000");
            this.instance = new KafkaProducer<>(props); 

            // Register standard JVM runtime hook to flush and close connections on app teardown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.log(System.Logger.Level.INFO, "Draining memory buffers and shutting down Kafka Producer...");
                this.instance.close(Duration.ofSeconds(5)); 
            }));
        }

        private KafkaProducer<String, String> instance() {
            return this.instance;
        }
    }
}
