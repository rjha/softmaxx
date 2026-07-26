import logging
import os 
import json
import math
from pathlib import Path
import psycopg
from psycopg.errors import UniqueViolation
from shapely.geometry import Polygon, box
from config import AppConfig, get_logger_config
from config import DatabaseType, get_database_config


logger = logging.getLogger("main." + __name__)


def get_database_conn_string():
    logger = logging.getLogger("main." + __name__)
    # 4. Resolve the targeted active runtime environment config block
    env_mode = os.environ.get("XAPI_ENV_MODE", "DEV").upper()
    db_type = DatabaseType.PRODUCTION if env_mode == "PRODUCTION" else DatabaseType.DEV
    db_config = get_database_config(db_type)

    # 5. Build positional format connection credentials
    DB_URI = "postgresql://{0}:{1}@{2}:{3}/{4}".format(
        db_config.db_user,
        db_config.db_password,
        db_config.db_host,
        db_config.db_port,
        db_config.db_name
    )

    logger.info("database URI -> " + DB_URI)
    return DB_URI


def _store_polygon_in_database(db_conn_string, polygon_name, polygon_object):
  
  with psycopg.connect(db_conn_string) as conn:
        with conn.cursor() as cur:
            insert_query = """
                INSERT INTO polygon_master (name, raw_geometry)
                VALUES (%s, %s)
                RETURNING polygon_id;
            """

            # Pass the python dictionary directly.
            # Psycopg automatically intercepts the dict and casts it into Postgres JSONB.
            cur.execute(insert_query, (polygon_name, psycopg.types.json.Jsonb(polygon_object)))
            # Fetch the auto-generated primary key
            polygon_id = cur.fetchone()[0]
            # Commit transaction safely
            conn.commit()
            return polygon_id


def _store_computation_in_database(db_conn_string, computation_name, zoom_level):
  with psycopg.connect(db_conn_string) as conn:
        with conn.cursor() as cur:
            insert_query = """
                INSERT INTO computation_master (name, zoom_level)
                VALUES (%s, %s)
                RETURNING computation_id;
            """

            cur.execute(insert_query, (computation_name, zoom_level))
            computation_id = cur.fetchone()[0]
            # Commit transaction safely
            conn.commit()
            return computation_id


def _get_intersecting_tiles(polygon_coords, zoom):
    """
    Finds and prints all XYZ tiles that truly intersect a given polygon,
    along with their custom packed 64-bit unsigned integer representations.
    
    :param polygon_coords: List of [longitude, latitude] coordinates (closed ring)
    :param zoom: Target tile zoom level (integer)
    """
    # 1. Instantiate the spatial polygon object
    poly_geom = Polygon(polygon_coords)
    
    # 2. Extract bounding box extremes to locate calculation limits
    min_lng, min_lat, max_lng, max_lat = poly_geom.bounds
    
    # Helper math equations to convert coordinates back and forth
    def lon2tile(lon, z):
        return math.floor((lon + 180) / 360 * (2 ** z))

    def lat2tile(lat, z):
        lat_rad = math.radians(lat)
        return math.floor((1 - math.log(math.tan(lat_rad) + 1.0 / math.cos(lat_rad)) / math.pi) / 2.0 * (2 ** z))

    def tile2lon(x, z):
        return x / (2 ** z) * 360.0 - 180.0

    def tile2lat(y, z):
        n = math.pi - 2.0 * math.pi * y / (2 ** z)
        return 180.0 / math.pi * math.atan(0.5 * (math.exp(n) - math.exp(-n)))

    # Compute bounding grid tile limits
    x_min = lon2tile(min_lng, zoom)
    x_max = lon2tile(max_lng, zoom)
    y_min = lat2tile(max_lat, zoom)   # Higher lat gives smaller Y index row
    y_max = lat2tile(min_lat, zoom)   # Lower lat gives larger Y index row
    
    print(f"--- Processing Zoom {zoom} True Intersections ---")
    print(f"Bounding Box Sweep: X Range [{x_min} to {x_max}], Y Range [{y_min} to {y_max}]")
    print(f"{'Z':<4} | {'X':<9} | {'Y':<9} | {'Packed UINT64 ID':<20}")
    print("-" * 53)
    
    intersect_count = 0
    
    # 3. Intersect loop iteration sweep
    for x in range(x_min, x_max + 1):
        for y in range(y_min, y_max + 1):
            # Reverse-engineer bounding coordinate edges for this specific tile cell
            w = tile2lon(x, zoom)
            e = tile2lon(x + 1, zoom)
            n = tile2lat(y, zoom)
            s = tile2lat(y + 1, zoom)
            
            # Form a Shapely bounding geometry box for evaluation
            tile_box = box(w, s, e, n)
            
            # 4. Filter true spatial geometry intersections
            if poly_geom.intersects(tile_box):
                intersect_count += 1
                
                # --- Bit-Packing Formula ---
                # Z: bits 0-5 (mask with 63)
                # X: bits 6-34 (shifted left by 6)
                # Y: bits 35-63 (shifted left by 35)
                packed_id = (int(y) << 35) | (int(x) << 6) | int(zoom)
                
                print(f"{zoom:<4} | {x:<9} | {y:<9} | {packed_id:<20}")
                
    print("-" * 53)
    print(f"Calculation Complete. Found {intersect_count} true intersecting tiles.\n")


def add_computation(computation_name, zoom_level):
    db_conn_string = get_database_conn_string()
    try:
        computation_id = _store_computation_in_database(db_conn_string, computation_name, zoom_level) 
        logger.info("computation inserted with id: " + str(computation_id))
    except UniqueViolation:
        logger.info("A computation with the name {0} already exists!".format(computation_name))


def add_geo_polygon(file_name, polygon_name):
    file_path = Path(file_name)
    if not file_path.exists() or not file_path.is_file():
        logger.error(f"Target GeoJSON source path does not exist: {file_path}")
        raise FileNotFoundError(f"Missing file: {file_path}")

    with file_path.open("r", encoding="utf-8") as file:
        geometry = json.load(file)
    
    # 3. Validate geometry type and extract coordinates
    geom_type = geometry.get("type")
    if geom_type not in ["Polygon", "MultiPolygon"]:
        raise ValueError(
            f"Unsupported geometry type: {geom_type}. Expected Polygon or MultiPolygon."
        )

    
    db_conn_string = get_database_conn_string()
    try:
        polygon_id = _store_polygon_in_database(db_conn_string, polygon_name, geometry) 
        logger.info("polygon inserted with id: " + str(polygon_id))
    except UniqueViolation:
        logger.info("A polygon with the name {0} already exists!".format(polygon_name))

    
def start_worker():
    print(f"start geo polygon process under PID: {os.getpid()}...")
    AppConfig.load()
    log_config = get_logger_config("global")
    AppConfig.init_logging(log_file=log_config.log_file, log_level=log_config.log_level)
    logger.info(f"geo polygon log config loaded...")
    # add_geo_polygon("polygon.json", "patna_zoo")
    add_computation("NDVI", 16)


if __name__ == "__main__":
    start_worker()