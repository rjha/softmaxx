package online.softmaxx.xapi.service.otp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import online.softmaxx.xapi.bundle.SysErrorCode;
import online.softmaxx.xapi.db.DataAccessException;
import online.softmaxx.xapi.db.DatabaseManager;
import online.softmaxx.xapi.service.model.PhoneRecord;
import java.util.Objects;


public class OtpTransaction {
    

    private static final System.Logger LOGGER = System.getLogger(OtpTransaction.class.getName());
    
    private OtpTransaction() {
        throw new UnsupportedOperationException("OtpTransaction class cannot be instantiated");
    }

    public static void saveOtpToken(final PhoneRecord phoneRecord, 
        final String token, 
        final OtpType otpType) {

        Objects.requireNonNull(phoneRecord, "PhoneRecord cannot be null");
        Objects.requireNonNull(otpType, "OtpType cannot be null");
        Connection conn = null;
            
        final String payload = "code: " + token + ",phone:" + phoneRecord.e164Phone();
        final String workerQueueSql  = "SELECT pgque.send(?, ?);";

        try {

            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);
            OtpDao.saveOtpToken(conn, phoneRecord, token, otpType);

            try (PreparedStatement psQue = conn.prepareStatement(workerQueueSql)) {
                psQue.setString(1, "xapi_tube");      
                psQue.setString(2, payload);
                psQue.executeQuery(); 
            }

            conn.commit();

        } catch (final SQLException e) {
            DatabaseManager.rollback(conn);
            LOGGER.log(System.Logger.Level.ERROR, "Database error during otp token save", e);
            throw new DataAccessException(SysErrorCode.DATABASE_CRASH.token(), e);

        } catch(final Exception ex) {
            DatabaseManager.rollback(conn);
            LOGGER.log(System.Logger.Level.ERROR, "Unexpected database failure inside saveOtpToken transaction", ex);
            throw new DataAccessException(SysErrorCode.DATABASE_CRASH.token(), ex);

        } finally {
            DatabaseManager.release(conn);
        }

    }

    public static boolean validateToken(final OtpVerificationRequest verificationRequest) {

        Connection conn = null;
        
        try {
            // do not start Transaction
            conn = DatabaseManager.getConnection();
            return OtpDao.validateToken(conn, verificationRequest);

        } catch (final SQLException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Database error executing OTP token validation", e);
            throw new DataAccessException(SysErrorCode.DATABASE_CRASH.token(), e);
        } finally {
            DatabaseManager.release(conn);
        }

    }


    public static void resetToken(final String e164Phone) {

        Connection conn = null;
            
        try {

            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);
            OtpDao.resetToken(conn, e164Phone);
            conn.commit();

        } catch (final SQLException e) {
            DatabaseManager.rollback(conn);
            LOGGER.log(System.Logger.Level.ERROR, "Database error during otp token reset", e);
            throw new DataAccessException(SysErrorCode.DATABASE_CRASH.token(), e);

        } catch(final Exception ex) {
            DatabaseManager.rollback(conn);
            throw new DataAccessException(SysErrorCode.DATABASE_CRASH.token(), ex);
        } finally {
            DatabaseManager.release(conn);
        }

    }



}
