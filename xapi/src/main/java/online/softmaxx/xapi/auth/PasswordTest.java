package online.softmaxx.xapi.auth;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import online.softmaxx.xapi.db.DatabaseManager;


public class PasswordTest {
    public static void main(String[] args) {

        PasswordService passwordService = new PasswordService();

        // 1. Prepare modern mobile-first sample registration data
        String userKey = "USR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String userName = "John Doe";
        String plainPassword = "hard-head-poncho";
        String countryCode = "+91";
        String phoneNumber = "9886224429";
        String e164Phone = countryCode + phoneNumber; 
        // Email remains null to support phone-only setups
        String email = null; 
        String localeCode = "en_IN";

        // Hash password at the Java Application layer using Bouncy Castle Argon2id
        String originalHash = passwordService.hashPassword(plainPassword);

        System.out.println("🚀 [PHASE 1: WRITE] Inserting user record into xapi_user...");
        
        String insertSql = 
        """
        INSERT INTO xapi_user (user_key, 
                            user_name, 
                            password_hash, 
                            country_code, 
                            phone_number, 
                            e164_phone, 
                            email, 
                            locale_code)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id ;
        """;
                
        int generatedId = -1;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            insertStmt.setString(1, userKey);
            insertStmt.setString(2, userName);
            insertStmt.setString(3, originalHash);
            insertStmt.setString(4, countryCode);
            insertStmt.setString(5, phoneNumber);
            insertStmt.setString(6, e164Phone);
            insertStmt.setNull(7, java.sql.Types.VARCHAR); 
            insertStmt.setString(8, localeCode);

            try (ResultSet rs = insertStmt.executeQuery()) {
                if (rs.next()) {
                    generatedId = rs.getInt("id");
                    System.out.println("✅ User inserted successfully. Assigned userKey: " + userKey);
                    System.out.println("✅ User inserted successfully. Assigned primaryKey: " + generatedId);
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Database write error: " + e.getMessage());
            e.printStackTrace();
            DatabaseManager.shutdown();
            return;
        }

        System.out.println("\n🔍 [PHASE 2: READ & VERIFY] get password out of PostgreSQL...");
        
        String selectSql = "SELECT password_hash FROM xapi_user WHERE user_key = ?";
        
        try (Connection conn = DatabaseManager.getConnection();

            PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            selectStmt.setString(1, userKey);

            try (ResultSet rs = selectStmt.executeQuery()) {

                if (rs.next()) {
                    String storedHashFromDb = rs.getString("password_hash");
                    
                    System.out.println("📥 Stored Hash Retrieved: " + storedHashFromDb);
                    System.out.println("🧐 Verifying plain password against retrieved hash...");

                    // Execute time-constant memory match verification
                    boolean matches = passwordService.verifyPassword(plainPassword, storedHashFromDb);

                    System.out.println("\n==============================================");
                    if (matches) {
                        System.out.println("🎉 VERIFICATION MATCH SUCCESSFUL!");
                        System.out.println("   The Bouncy Castle Argon2id engine successfully");
                        System.out.println("   authenticated the plain password against the DB string.");
                    } else {
                        System.err.println("🚨 VERIFICATION FAILURE!");
                        System.err.println("   The generated match function returned false.");
                    }
                    System.out.println("==============================================");
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Database fetch error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Gracefully terminate connection pool
            DatabaseManager.shutdown();
        }
    }
}

