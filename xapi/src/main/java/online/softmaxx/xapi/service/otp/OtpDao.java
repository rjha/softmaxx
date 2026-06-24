package online.softmaxx.xapi.service.otp;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

import online.softmaxx.xapi.bundle.AppErrorCode;
import online.softmaxx.xapi.bundle.SysErrorCode;
import online.softmaxx.xapi.service.model.PhoneRecord;
import online.softmaxx.xapi.db.DataAccessException;
import online.softmaxx.xapi.db.DatabaseManager; 


public final class OtpDao {

    private OtpDao() {} 
    private static final System.Logger LOGGER = System.getLogger(OtpDao.class.getName());
    private static final long SMS_COOLDOWN_SECONDS = 60;

    /**
     * Persists the generated token payload details into the otp_token PostgreSQL 18 table.
     * 
     * @param phoneRecord Domain model carrying country code and subscriber number details
     * @param token        The secure payload string created by the token factory
     * @param otpType      The configuration context defining parameters like timeToLiveSeconds
     */

    public static void saveOtpToken(final PhoneRecord phoneRecord, final String token, final OtpType otpType) {

        Objects.requireNonNull(phoneRecord, "PhoneRecord cannot be null");
        Objects.requireNonNull(otpType, "OtpType cannot be null");

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(AppErrorCode.TOKEN_OTP_BLANK.token());
        }

        // 2. Calculate database Unix epoch expiry timestamp
        final long expireOnEpochSeconds = (System.currentTimeMillis() / 1000) + otpType.timeToLiveSeconds();

        // 3. High-performance Upsert query matching PostgreSQL18 
        // standard features. 
        // (1) UPSERT new token for the same phone and move expire_on 
        // (2) Do not do the write if the where condition fails, ignoring 
        //  requests that arrive before 60 seconds for the same number.
        
        final String sql = """
           INSERT INTO otp_token (phone_number, country_code, e164_phone, token, expire_on)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (e164_phone) 
            DO UPDATE SET 
                token = EXCLUDED.token,
                expire_on = EXCLUDED.expire_on,
                updated_at = CURRENT_TIMESTAMP
            WHERE EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - otp_token.updated_at)) >= ?;
            """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, phoneRecord.phoneNumber());
            ps.setString(2, phoneRecord.countryCode());
            ps.setString(3, phoneRecord.e164Phone());
            ps.setString(4, token);
            ps.setLong(5, expireOnEpochSeconds);
            ps.setLong(6, SMS_COOLDOWN_SECONDS);

            final int rowsAffected = ps.executeUpdate();
            // If rowsAffected is 0, the database rejected 
            // the save operation because of the timing constraint.
            if (rowsAffected == 0) {
                throw new IllegalArgumentException(SysErrorCode.TOO_MANY_REQUESTS.token());
            }


        } catch (SQLException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Database error during otp token save", e);
            throw new DataAccessException(SysErrorCode.DATABASE_CRASH.token(), e);
        }
    }
}
