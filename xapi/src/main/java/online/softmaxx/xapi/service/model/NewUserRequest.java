package online.softmaxx.xapi.service.model;
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
        if (isInvalid(userName)) throw new IllegalArgumentException("Username cannot be blank");
        if (isInvalid(password)) throw new IllegalArgumentException("Password cannot be blank");
        if (isInvalid(countryCode)) throw new IllegalArgumentException("Country code cannot be blank");
        if (isInvalid(phoneNumber)) throw new IllegalArgumentException("Phone number cannot be blank");
        if (isInvalid(localeCode)) throw new IllegalArgumentException("Locale code cannot be blank");
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
