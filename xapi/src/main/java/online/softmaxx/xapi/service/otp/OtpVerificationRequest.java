package online.softmaxx.xapi.service.otp;

import online.softmaxx.xapi.bundle.AppErrorCode;
import online.softmaxx.xapi.service.model.PhoneRecord;


public record OtpVerificationRequest(

    PhoneRecord phoneRecord,
    String token
) {
    public OtpVerificationRequest {

        if (phoneRecord == null) {
            throw new IllegalArgumentException(AppErrorCode.PHONE_BLANK.token());
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(AppErrorCode.TOKEN_OTP_BLANK.token());
        }
    }

    public static OtpVerificationRequest create(final OtpVerificationParam param) {

        if (param == null) {
            throw new IllegalArgumentException("Verification payload parameters cannot be null");
        }
        // Leveraging PhoneRecord's constructor to auto-validate individual phone components
        final PhoneRecord phone = new PhoneRecord(param.countryCode(), param.phoneNumber());
        return new OtpVerificationRequest(phone, param.token().trim());

    }
}