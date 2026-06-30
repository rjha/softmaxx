package online.softmaxx.xapi.kafka;


import java.util.Properties;
import java.time.Duration;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
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
        // and catch the error right where it happens
        PROVIDER.instance().send(record, (metadata, exception) -> {
            if (exception == null) {
                // send() is success
                LOG_TRACKER.reset();
                // @todo change to DEBUG 
                LOGGER.log(System.Logger.Level.INFO, 
                    "Message saved to topic={0} partition={1} offset={2}",
                    metadata.topic(), 
                    metadata.partition(), 
                    metadata.offset());
                
            } else {
                LOG_TRACKER.error( "kafka delivery failed for topic: " + topic, exception);
            }
        });
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
