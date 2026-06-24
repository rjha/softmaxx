package online.softmaxx.xapi.service.otp;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import online.softmaxx.xapi.bundle.AppErrorCode;
import online.softmaxx.xapi.bundle.SysErrorCode;
import online.softmaxx.xapi.service.model.PhoneRecord;
 


public final class OtpDao {

    private OtpDao() {} 
    private static final long SMS_COOLDOWN_SECONDS = 60;

    /**
     * Persists the generated token payload details into the otp_token PostgreSQL 18 table.
     * 
     * @param phoneRecord Domain model carrying country code and subscriber number details
     * @param token        The secure payload string created by the token factory
     * @param otpType      The configuration context defining parameters like timeToLiveSeconds
     */

    public static void saveOtpToken(final Connection conn, 
        final PhoneRecord phoneRecord, 
        final String token, 
        final OtpType otpType) throws SQLException {

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

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            
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

        } 
    }

    
    public static boolean validateToken(final Connection conn, 
        final OtpVerificationRequest verificationRequest) throws SQLException {

        Objects.requireNonNull(verificationRequest, "OtpRequest cannot be null");

        // Added ORDER BY id DESC to prioritize 
        // the latest generated token record
        final String sql = """
            SELECT 1 FROM otp_token 
            WHERE e164_phone = ? 
            AND token = ? 
            AND expire_on > EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)
            ORDER BY id DESC
            LIMIT 1;
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, verificationRequest.phoneRecord().e164Phone());
            ps.setString(2, verificationRequest.token());
            
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next(); 
            }

        }

    }

    // Invalidates a consumed token by setting its expiration timestamp to 
    // the past. This securely locks out replay attacks while avoiding 
    // expensive row deletions. 
    public static void resetToken(final Connection conn, final String e164Phone) throws SQLException {

        java.util.Objects.requireNonNull(e164Phone, "E164 phone parameter cannot be null");

        // Sets the expire_on timestamp to 0 (January 1st, 1970), 
        // instantly failing future "expire_on > NOW" checks.
        final String sql = """
            UPDATE otp_token 
            SET expire_on = 0, 
                updated_on_at = CURRENT_TIMESTAMP 
            WHERE e164_phone = ?;
            """;

        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e164Phone);
            ps.executeUpdate();
        }
        
    }

}
