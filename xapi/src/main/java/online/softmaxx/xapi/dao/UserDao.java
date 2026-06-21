package online.softmaxx.xapi.dao;

import online.softmaxx.xapi.util.KeyGenerator;
import online.softmaxx.xapi.service.model.*;
import online.softmaxx.xapi.auth.PasswordService;
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
            LOGGER.log(System.Logger.Level.ERROR, "Database interaction failed during user creation operation", e);
            
            if ("23505".equals(e.getSQLState())) {
                final String specificField = e.getMessage().contains("e164_phone") ? "Phone number" : "Email";
                throw new DuplicateKeyException(specificField + " is already in use.");
            }
            
            throw new DataAccessException("Database operation failed unexpectedly.", e);
        }
    }
}


