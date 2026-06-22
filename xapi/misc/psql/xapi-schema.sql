

connect using the xapi_user to xapi_db 
$ psql -U xapi_user -d xapi_db


-- 1. Clear any old tables
DROP TABLE IF EXISTS xapi_user;

-- 2. Create the xapi_user table with nullable email
CREATE TABLE xapi_user (
    id SERIAL PRIMARY KEY,
    user_key VARCHAR(100) UNIQUE NOT NULL,    
    user_name VARCHAR(50) NOT NULL,           
    password_hash VARCHAR(255) NOT NULL, 
    country_code VARCHAR(5) NOT NULL,    
    phone_number VARCHAR(15) NOT NULL,   
    e164_phone VARCHAR(20) UNIQUE NOT NULL,   
    -- Nullable to support phone-only registrations       
    email VARCHAR(100),                       
    locale_code VARCHAR(10) NOT NULL,         
    is_active SMALLINT NOT NULL DEFAULT 1,    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Maintain standard lookup indices
CREATE INDEX idx_xapi_user_name ON xapi_user(user_name);
CREATE INDEX idx_xapi_user_e164_phone ON xapi_user(e164_phone);

-- 4. Create a Partial Unique Index for email
-- This enforces uniqueness ONLY when email is not null, allowing multiple null entries.
CREATE UNIQUE INDEX idx_xapi_user_email_unique ON xapi_user(email) WHERE email IS NOT NULL;



