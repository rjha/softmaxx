package online.softmaxx.xapi.service.otp;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OtpRequestParam(
    @JsonProperty("countryCode") String countryCode,
    @JsonProperty("phoneNumber") String phoneNumber
) {}
