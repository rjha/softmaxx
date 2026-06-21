package online.softmaxx.xapi.service.model;
import online.softmaxx.xapi.service.error.AppMessage;
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

        if (isInvalid(userName)) throw new IllegalArgumentException(AppMessage.USERNAME_BLANK.token());
        if (isInvalid(password)) throw new IllegalArgumentException(AppMessage.PASSWORD_BLANK.token());
        if (isInvalid(countryCode)) throw new IllegalArgumentException(AppMessage.COUNTRY_BLANK.token());
        if (isInvalid(phoneNumber)) throw new IllegalArgumentException(AppMessage.PHONE_BLANK.token());
        if (isInvalid(localeCode)) throw new IllegalArgumentException(AppMessage.LOCALE_BLANK.token());

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
