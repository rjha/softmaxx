package online.softmaxx.xapi.service.param;


import com.fasterxml.jackson.annotation.JsonProperty;

public record UserLoginParam(
    @JsonProperty("countryCode") String countryCode,
    @JsonProperty("phoneNumber") String phoneNumber,
    @JsonProperty("password") String password
) {}

