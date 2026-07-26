

connect using the xapi_user to xapi_db 
$ psql -U xapi_user -d xapi_db



DROP TABLE IF EXISTS xapi_user CASCADE;
CREATE TABLE xapi_user (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_key VARCHAR(100) UNIQUE NOT NULL,    
    user_name VARCHAR(50) NOT NULL,           
    password_hash VARCHAR(255) NOT NULL, 
    country_code VARCHAR(5) NOT NULL,    
    phone_number VARCHAR(15) NOT NULL,   
    e164_phone VARCHAR(20) UNIQUE NOT NULL,   
    email VARCHAR(100),                       
    locale_code VARCHAR(10) NOT NULL,         
    is_active BOOLEAN NOT NULL DEFAULT TRUE, -- Fixed legacy MySQL pattern   
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 3. Maintain standard lookup indices
CREATE INDEX idx_xapi_user_name ON xapi_user(user_name);
CREATE INDEX idx_xapi_user_e164_phone ON xapi_user(e164_phone);

-- 4. Create a Partial Unique Index for email
-- This enforces uniqueness ONLY when email is not null, allowing multiple null entries.
CREATE UNIQUE INDEX idx_xapi_user_email_unique ON xapi_user(email) WHERE email IS NOT NULL;



DROP TABLE IF EXISTS otp_token CASCADE;

CREATE TABLE otp_token (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, 
  country_code VARCHAR(5) NOT NULL,    
  phone_number VARCHAR(15) NOT NULL, 
  e164_phone VARCHAR(20) UNIQUE NOT NULL,   
  token VARCHAR(8) NOT NULL,
  expire_on BIGINT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);


CREATE INDEX idx_otp_token_lookup ON otp_token (e164_phone, expire_on DESC);


-- 
-- computation on grid tiles schema 
--

-- 
-- computations in system 
-- every computation for grid tiles should have a 
-- natural resolution (the z-level in xyz tiles)

CREATE TABLE computation_master (
    computation_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    zoom_level INTEGER NOT NULL CHECK (zoom_level BETWEEN 0 AND 30),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 
-- 2: store polygon data as binary JSON 
--

CREATE TABLE polygon_master (
    polygon_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    raw_geometry JSONB NOT NULL, 
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);


CREATE INDEX idx_polygon_master_geometry ON polygon_master USING GIN (raw_geometry);

-- 
-- 3. when a polygon subscribes a computation, record the subscription, 
-- generate new computation_tile or find existing and update in 
-- polygon_tile_mapper and computation_tile_mapper 
-- 

CREATE TABLE polygon_subscription (
    subscription_id BIGSERIAL PRIMARY KEY,
    polygon_id BIGINT NOT NULL REFERENCES polygon_master(polygon_id) ON DELETE CASCADE,
    computation_id BIGINT NOT NULL REFERENCES computation_master(computation_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_polygon_zoom UNIQUE (polygon_id, computation_id)
);


-- 4. Unique computation tile for a zoom level 
-- multiple computations will link to this table 
-- @todo index 

CREATE TABLE computation_tile (
    tile_id BIGSERIAL PRIMARY KEY,
    zoom_level INTEGER NOT NULL CHECK (zoom_level BETWEEN 0 AND 30),
    tile_x INTEGER NOT NULL,
    tile_y INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_compuation_tile UNIQUE (zoom_level, tile_x, tile_y)
);

CREATE INDEX idx_comp_tile_xyz ON computation_tile(zoom_level, tile_x, tile_y);

-- 5. resolve a polygon into tiles at a zoom level 
-- area_fraction is a measure of tile area enclosed by polygon 
-- An outside process will populate this table. We add an entry for 
-- every computation_tile  
-- 

CREATE TABLE polygon_tile_mapper (
    mapper_id BIGSERIAL PRIMARY KEY,
    polygon_id BIGINT NOT NULL REFERENCES polygon_master(polygon_id) ON DELETE CASCADE,
    tile_id BIGINT NOT NULL REFERENCES computation_tile(tile_id) ON DELETE RESTRICT,
    area_fraction NUMERIC(5, 4) NOT NULL CHECK (area_fraction > 0 AND area_fraction <= 1),
    CONSTRAINT unique_polygon_tile_mapper UNIQUE (polygon_id, tile_id)
);

CREATE INDEX idx_polygon_comp_tile ON polygon_tile_mapper(tile_id);

--
-- 6. what tiles a computation is supposed to run on 
-- 
-- 
CREATE TABLE computation_tile_mapper (
    mapper_id BIGSERIAL PRIMARY KEY,
    computation_id BIGINT REFERENCES computation_master(computation_id) ON DELETE CASCADE,
    tile_id BIGINT REFERENCES computation_tile(tile_id) ON DELETE CASCADE,
    CONSTRAINT unique_computation_tile_mapper UNIQUE (computation_id, tile_id)
);

CREATE INDEX idx_comp_comp_tile ON computation_tile_mapper(computation_id);


---
--- 7. 
--- 

CREATE TABLE computation_run (
    run_uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    computation_id BIGINT REFERENCES computation_master(computation_id),
    status VARCHAR(50) DEFAULT 'PENDING', -- PENDING, PROCESSING, COMPLETED, FAILED
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_comp_run_comp ON computation_run(computation_id);
CREATE INDEX idx_comp_run_status ON computation_run(status) WHERE status IN ('PENDING', 'PROCESSING');


-- Stores the computation output (metrics, multi-values, or arrays)
-- against a tile on the grid 
-- 


CREATE TABLE computation_result (
    result_id BIGSERIAL PRIMARY KEY,
    run_uuid UUID REFERENCES computation_run(run_uuid) ON DELETE CASCADE, 
    tile_id BIGINT REFERENCES computation_tile(tile_id) ON DELETE CASCADE,
    result JSONB NOT NULL, 
    computed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_run_tile_result UNIQUE (run_uuid, comp_tile_id)
);

CREATE INDEX idx_comp_result_run ON computation_result(run_uuid);
CREATE INDEX idx_comp_result_data ON computation_result USING GIN (result);

