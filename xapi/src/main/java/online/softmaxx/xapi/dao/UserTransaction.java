package online.softmaxx.xapi.dao;

import java.sql.Connection;
import java.sql.SQLException;
import online.softmaxx.xapi.bundle.SysErrorCode;
import online.softmaxx.xapi.service.model.*;
import online.softmaxx.xapi.db.* ;


public final class UserTransaction {

    private static final System.Logger LOGGER = System.getLogger(UserTransaction.class.getName());
    
    private UserTransaction() {
        throw new UnsupportedOperationException("UserTransaction class cannot be instantiated");
    }

    public static String registerNewUser(NewUserRequest newUserRequest) {

        Connection conn = null;
        String userKey = null;

        try {

            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);
            userKey = UserDao.registerNewUser(conn, newUserRequest);
            conn.commit();
            return userKey;

        } catch (final SQLException e) {

            DatabaseManager.rollback(conn);
            LOGGER.log(System.Logger.Level.ERROR, "Fatal Database error during new user registration", e);
            
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

        } catch(final Exception ex) {
            DatabaseManager.rollback(conn);
            throw new DataAccessException(SysErrorCode.DATABASE_CRASH.token(), ex);
        } finally {
            DatabaseManager.release(conn);
        }

    }


    public static String authenticateUser(final UserLoginRequest userLoginRequest) {

        Connection conn = null;
        
        try {
            // do not start Transaction
            conn = DatabaseManager.getConnection();
            return UserDao.authenticateUser(conn, userLoginRequest);

        } catch (final SQLException e) {

            LOGGER.log(System.Logger.Level.ERROR, "Database error during authentication sequence", e);
            throw new DataAccessException(SysErrorCode.DATABASE_CRASH.token(), e);
        } finally {
            DatabaseManager.release(conn);
        }

    }


    public static boolean phoneExists(final String e164Phone) {

        Connection conn = null;
        
        try {
            // do not start Transaction
            conn = DatabaseManager.getConnection();
            return UserDao.phoneExists(conn, e164Phone);

        } catch (final SQLException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Database error during phone lookup", e);
            throw new DataAccessException(SysErrorCode.DATABASE_CRASH.token(), e);
        } finally {
            DatabaseManager.release(conn);
        }

    }

    public static String getUserKeyByPhone(final String e164Phone) {

        Connection conn = null;
        
        try {
            // do not start Transaction
            conn = DatabaseManager.getConnection();
            return UserDao.getUserKeyByPhone(conn, e164Phone);

        } catch (final SQLException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Database error during userKeyOnPhone lookup", e);
            throw new DataAccessException(SysErrorCode.DATABASE_CRASH.token(), e); 
        } finally {
            DatabaseManager.release(conn);
        }

    }


}