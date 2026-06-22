package online.softmaxx.xapi.service.model;

import online.softmaxx.xapi.bundle.AppErrorCode;
import online.softmaxx.xapi.service.param.UserLoginParam;


public record UserLoginRequest(
    String countryCode,
    String phoneNumber,
    String password,
    String e164Phone
) {
 

    public UserLoginRequest {
        if (strInvalid(countryCode)) throw new IllegalArgumentException(AppErrorCode.COUNTRY_BLANK.token());
        if (strInvalid(phoneNumber)) throw new IllegalArgumentException(AppErrorCode.PHONE_BLANK.token());
        if (strInvalid(password)) throw new IllegalArgumentException(AppErrorCode.PASSWORD_BLANK.token());
    }
    
    public static UserLoginRequest create(final UserLoginParam param) {
        if (param == null) {
            throw new IllegalArgumentException("Login credentials parameter cannot be null.");
        }
        
        final String cleanCountry = param.countryCode().trim();
        final String cleanPhone = param.phoneNumber().trim();
        final String computedE164 = cleanCountry + cleanPhone;

        return new UserLoginRequest(cleanCountry, cleanPhone, param.password(), computedE164);
    }

    private static boolean strInvalid(final String str) {
        return str == null || str.isBlank();
    }
}

