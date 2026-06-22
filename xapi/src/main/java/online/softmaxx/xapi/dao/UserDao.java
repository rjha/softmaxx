package online.softmaxx.xapi.dao;

import online.softmaxx.xapi.util.KeyGenerator;
import online.softmaxx.xapi.service.model.*;
import online.softmaxx.xapi.auth.PasswordService;
import online.softmaxx.xapi.bundle.AppErrorCode;
import online.softmaxx.xapi.bundle.SysErrorCode;
import online.softmaxx.xapi.db.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;


public final class UserDao {

    private static final System.Logger LOGGER = System.getLogger(UserDao.class.getName());
   
    private UserDao() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String registerNewUser(final NewUserRequest model) {

        final String userKey = "USR-" + KeyGenerator.generateToken();
        final String cleanCountry = model.countryCode().trim();
        final String cleanPhone = model.phoneNumber().trim();
        final String e164Phone = cleanCountry + cleanPhone;
        
        final String passwordHash = PasswordService.hashPassword(model.password());
        final String sql = """
                INSERT INTO xapi_user (user_key, user_name, password_hash, 
                        country_code, phone_number, e164_phone, email, 
                        locale_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ;
                """;
        
        try (final Connection conn = DatabaseManager.getConnection();

            final PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userKey);
            pstmt.setString(2, model.userName().trim());
            pstmt.setString(3, passwordHash);
            pstmt.setString(4, cleanCountry);
            pstmt.setString(5, cleanPhone);
            pstmt.setString(6, e164Phone);
            
            if (model.email() == null || model.email().isBlank()) {
                pstmt.setNull(7, java.sql.Types.VARCHAR);
            } else {
                pstmt.setString(7, model.email().trim().toLowerCase());
            }
            
            pstmt.setString(8, model.localeCode().trim());
            
            pstmt.executeUpdate();
            LOGGER.log(System.Logger.Level.INFO, "User record added. Key: {0}", userKey);
            return userKey;

        } catch (final SQLException e) {

            LOGGER.log(System.Logger.Level.ERROR, "Fatal Database error during user creation", e);
            
            if ("23505".equals(e.getSQLState())) {

                final String errorMessage = e.getMessage();

                if(errorMessage.contains("e164_phone")) {
                    throw new DuplicateKeyException(SysErrorCode.DUPLICATE_PHONE.token());
                }

                if(errorMessage.contains("email")) {
                    throw new DuplicateKeyException(SysErrorCode.DUPLICATE_EMAIL.token());
                }
                
            }
            
            throw new DataAccessException(SysErrorCode.DATABASE_CRASH.token(), e);
        }
    }

    
    public static String authenticateUser(final UserLoginRequest model) {

        final String sql = "SELECT user_key, password_hash FROM xapi_user WHERE e164_phone = ?";

        try (final java.sql.Connection conn = DatabaseManager.getConnection();
             final java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
            pstmt.setString(1, model.e164Phone());
            
            try (final java.sql.ResultSet rs = pstmt.executeQuery()) {

                if (rs.next()) {

                    final String userKey = rs.getString("user_key");
                    final String storedHash = rs.getString("password_hash");

                    // Execute safe constant-time  
                    // cryptography matching via Bouncy Castle
                    if (PasswordService.verifyPassword(model.password(), storedHash)) {
                        return userKey;
                    }
                }
            }

            // Fail with generic validation indicator 
            // to prevent identity scraping vulnerabilities
            throw new IllegalArgumentException(AppErrorCode.INVALID_CREDENTIALS.token());

        } catch (final java.sql.SQLException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Database error during authentication sequence", e);
            throw new DataAccessException(SysErrorCode.DATABASE_CRASH.token(), e);
        }
    }

}


