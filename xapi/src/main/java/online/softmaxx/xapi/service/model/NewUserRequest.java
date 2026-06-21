package online.softmaxx.xapi.service.model;
import online.softmaxx.xapi.service.error.AppExceptionCode;
import online.softmaxx.xapi.service.param.NewUserRequestParam;


public record NewUserRequest(
    String userName,
    String password,
    String countryCode,
    String phoneNumber,
    String email,
    String localeCode
) {
    public NewUserRequest {

        if (isInvalid(userName)) throw new IllegalArgumentException(AppExceptionCode.USERNAME_BLANK.name());
        if (isInvalid(password)) throw new IllegalArgumentException(AppExceptionCode.PASSWORD_BLANK.name());
        if (isInvalid(countryCode)) throw new IllegalArgumentException(AppExceptionCode.COUNTRY_BLANK.name());
        if (isInvalid(phoneNumber)) throw new IllegalArgumentException(AppExceptionCode.PHONE_BLANK.name());
        if (isInvalid(localeCode)) throw new IllegalArgumentException(AppExceptionCode.LOCALE_BLANK.name());
        
    }

    public static NewUserRequest create(final NewUserRequestParam param) {

        if (param == null) {
            throw new IllegalArgumentException("Request payload parameters cannot be null");
        }

        return new NewUserRequest(
            param.userName(),
            param.password(),
            param.countryCode(),
            param.phoneNumber(),
            param.email(),
            param.localeCode()
        );

    }

    private static boolean isInvalid(final String str) {
        return str == null || str.isBlank();
    }

}
