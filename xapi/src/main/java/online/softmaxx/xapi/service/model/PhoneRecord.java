package online.softmaxx.xapi.service.model;

import online.softmaxx.xapi.bundle.AppErrorCode;
import online.softmaxx.xapi.service.otp.OtpRequestParam;

public record PhoneRecord(
    String countryCode,
    String phoneNumber
) {
    public PhoneRecord {
        if (isInvalid(countryCode)) throw new IllegalArgumentException(AppErrorCode.COUNTRY_BLANK.token());
        if (isInvalid(phoneNumber)) throw new IllegalArgumentException(AppErrorCode.PHONE_BLANK.token());
    }

    public static PhoneRecord create(final OtpRequestParam param) {
        if (param == null) {
            throw new IllegalArgumentException("Request payload parameters cannot be null");
        }
        return new PhoneRecord(param.countryCode(), param.phoneNumber());
    }

    private static boolean isInvalid(final String str) {
        return str == null || str.isBlank();
    }
}
