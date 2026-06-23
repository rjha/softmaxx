package online.softmaxx.xapi.service.otp;


public sealed interface OtpType permits OtpType.Numeric6Digit, OtpType.AlphaNumeric8Digit {

    int length();
    String templateId();
    boolean isAlphanumeric();
    long timeToLiveSeconds();

    // 1. Standard 6-Digit Verification Type (e.g., Login / Sign-up)
    record Numeric6Digit(
        String templateId, 
        long timeToLiveSeconds
    ) implements OtpType {
        @Override public int length() { return 6; }
        @Override public boolean isAlphanumeric() { return false; }
    }

    // 2. Strong 8-Character Alphanumeric Type (e.g., High-security transactions)
    record AlphaNumeric8Digit(
        String templateId, 
        long timeToLiveSeconds
    ) implements OtpType {
        @Override public int length() { return 8; }
        @Override public boolean isAlphanumeric() { return true; }
    }
}