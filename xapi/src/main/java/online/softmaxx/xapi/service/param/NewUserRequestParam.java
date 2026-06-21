package online.softmaxx.xapi.service.param;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NewUserRequestParam(
    @JsonProperty("userName") String userName,
    @JsonProperty("password") String password,
    @JsonProperty("countryCode") String countryCode,
    @JsonProperty("phoneNumber") String phoneNumber,
    @JsonProperty("email") String email, 
    @JsonProperty("localeCode") String localeCode
) {}
