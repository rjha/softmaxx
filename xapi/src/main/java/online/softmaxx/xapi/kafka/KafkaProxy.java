package online.softmaxx.xapi.kafka;

import online.softmaxx.xapi.service.model.PhoneRecord;
import online.softmaxx.xapi.service.otp.OtpType;

public final class KafkaProxy {
    
    private KafkaProxy() {
        throw new UnsupportedOperationException("KafkaProxy class cannot be instantiated");
    }

    public static void sendOtpEvent(final PhoneRecord phoneRecord, 
        final String token, 
        final OtpType otpType) {

        final String payload = "code: " + token + ",phone:" + phoneRecord.e164Phone();
        // Forward execution to the Kafka client
        KafkaPublisher.send("xapi_tube", phoneRecord.e164Phone(), payload);

    }
}
