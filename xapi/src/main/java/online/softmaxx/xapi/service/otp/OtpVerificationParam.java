package online.softmaxx.xapi.service.otp;

import com.fasterxml.jackson.annotation.JsonProperty;


public record OtpVerificationParam(
    @JsonProperty("countryCode") String countryCode,
    @JsonProperty("phoneNumber") String phoneNumber,
    @JsonProperty("token") String token
) {}