import logging
import os 
import json
import math
from pathlib import Path
from dataclasses import dataclass
import psycopg
from psycopg.errors import UniqueViolation
from shapely.geometry import Polygon, box
from config import AppConfig, get_logger_config
from config import DatabaseType, get_database_config


logger = logging.getLogger("main." + __name__)


@dataclass(frozen=True)
class GeoTile:
    x: int
    y: int
    z: int
    area_fraction: float

@dataclass(frozen=True)
class ComputationDetail:
    id: int
    name: str
    zoom_level: int

@dataclass(frozen=True)
class PolygonDetail:
    id: int
    name: str
    geometry: str


# Constants for Web Mercator (EPSG:3857) projection math
EARTH_RADIUS = 6378137.0
ORIGIN_SHIFT = math.pi * EARTH_RADIUS  # ~20037508.34 meters


def _latlon_to_meters(lon: float, lat: float) -> tuple:
    """Converts WGS84 Longitude/Latitude to Web Mercator meters (EPSG:3857)

    using pure mathematical equations.
    """
    # Convert longitude to meters
    mx = lon * ORIGIN_SHIFT / 180.0

    # Convert latitude to meters (handle poles safeguard)
    if lat > 85.05112878:
        lat = 85.05112878
    elif lat < -85.05112878:
        lat = -85.05112878

    my = (
        math.log(math.tan((90.0 + lat) * math.pi / 360.0))
        / (math.pi / 180.0)
    )
    my = my * ORIGIN_SHIFT / 180.0

    return mx, my


def _project_polygon_to_meters(coords_list: list) -> Polygon:
    """Projects a list of [[lon, lat], ...] coordinates into a Shapely Polygon

    expressed in Web Mercator meters.
    """
    meter_coords = [_latlon_to_meters(lon, lat) for lon, lat in coords_list]
    return Polygon(meter_coords)



def _get_intersecting_tiles(polygon_coords, zoom):
    """
    Finds and prints all XYZ tiles that truly intersect a given polygon,
    along with their custom packed 64-bit unsigned integer representations.
    
    :param polygon_coords: List of [longitude, latitude] coordinates (closed ring)
    :param zoom: Target tile zoom level (integer)
    """

    poly_geom_3857 = _project_polygon_to_meters(polygon_coords)
    # Keep your original WGS84 polygon for fast .intersects() bounding checks
    poly_geom_wgs84 = Polygon(polygon_coords)
    min_lng, min_lat, max_lng, max_lat = poly_geom_wgs84.bounds

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
    tiles = []

    for x in range(x_min, x_max + 1):
        for y in range(y_min, y_max + 1):
            w = tile2lon(x, zoom)
            e = tile2lon(x + 1, zoom)
            n = tile2lat(y, zoom)
            s = tile2lat(y + 1, zoom)

            tile_box_wgs84 = box(w, s, e, n)

            # Fast bounding check first
            if poly_geom_wgs84.intersects(tile_box_wgs84):

                # 2. Project the 4 corners of the tile box 
                # into meters using your math function
                tile_box_3857 = box(
                    *_latlon_to_meters(w, s), *_latlon_to_meters(e, n)
                )

                # 3. Calculate intersection area fraction in meters
                intersection_geom = poly_geom_3857.intersection(tile_box_3857)
                area_fraction = intersection_geom.area / tile_box_3857.area
                area_fraction = round(area_fraction, 4)

                # Skip zero-sliver tiles to avoid database CheckViolations
                if area_fraction > 0:
                    intersect_count += 1
                    packed_id = (int(y) << 35) | (int(x) << 6) | int(zoom)
                    print(f"{zoom:<4} | {x:<9} | {y:<9} | {packed_id:<20}")
                    tiles.append(
                        GeoTile(
                            x=int(x),
                            y=int(y),
                            z=int(zoom),
                            area_fraction=area_fraction,
                        )
                    )

    print("-" * 53)
    print(f"Calculation Complete. Found {intersect_count} true intersecting tiles.\n")
    return tiles


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


def _create_polygon_computation(conn, polygon_id, computation_id):
    query = """
        INSERT INTO polygon_computation(polygon_id, computation_id)
        VALUES (%s, %s)
        RETURNING polygon_comp_id;
    """
    with conn.cursor() as cur:
        try:
            cur.execute(query, (polygon_id, computation_id))
        except UniqueViolation as e:
            logger.error(f"record already exists for polygon_id={polygon_id} and computation_id={computation_id}")
            raise e


def _create_geo_tile(conn: psycopg.Connection, tile: GeoTile) -> int:
    """Finds or creates a tile in the geo_tiles table. """

    with conn.cursor() as cur:
       
        insert_query = """
            INSERT INTO geo_tiles (tile_z, tile_x, tile_y)
            VALUES (%s, %s, %s)
            ON CONFLICT (tile_z, tile_x, tile_y) DO NOTHING
            RETURNING tile_id;
        """
        cur.execute(insert_query, (tile.z, tile.x, tile.y))

        # fetchone will return a tuple 
        # we need to ensure that we return the first item of tuple
        result = cur.fetchone()
        if result:
            return result[0] 

        # 2. If result is None, the tile already existed. Fetch its ID.
        select_query = """
            SELECT tile_id 
            FROM geo_tiles 
            WHERE tile_z = %s AND tile_x = %s AND tile_y = %s;
        """
        cur.execute(select_query, (tile.z, tile.x, tile.y))
        existing_result = cur.fetchone()
        if not existing_result:
            raise ValueError(f"fatal: tile x:{tile.x} y:{tile.y} z:{tile.z} not created or found!")
        return existing_result[0]


def _create_polygon_tile(conn: psycopg.Connection, polygon_id: int, tile_id: int, area_fraction: float) -> int:
   
    with conn.cursor() as cur:
        # 1. Attempt insertion. 
        # If it already exists, do nothing but keep transaction clean.
        insert_query = """
            INSERT INTO polygon_tiles (polygon_id, tile_id, area_fraction)
            VALUES (%s, %s, %s)
            ON CONFLICT (polygon_id, tile_id) DO NOTHING
        """
        cur.execute(insert_query, (polygon_id, tile_id, area_fraction))


def _create_computation_tile(conn: psycopg.Connection, computation_id: int, tile_id: int) -> int:
   
    with conn.cursor() as cur:
        # 1. Attempt insertion. 
        # If it already exists, do nothing but keep transaction clean.
        insert_query = """
            INSERT INTO computation_tiles (computation_id, tile_id)
            VALUES (%s, %s)
            ON CONFLICT (computation_id, tile_id) DO NOTHING
        """
        cur.execute(insert_query, (computation_id, tile_id))


def _get_computation_detail(conn: psycopg.Connection, name: str) -> tuple:
    with conn.cursor() as cur:
        select_query = """
            SELECT computation_id, name, zoom_level
            FROM computation_master
            WHERE name = %s;
        """
        cur.execute(select_query, (name,))
        result = cur.fetchone()

        if not result:
            logger.error(f"Computation with name '{name}' not found.")
            raise ValueError(f"Computation '{name}' does not exist.")

        # 2. Instantiate and return the clean object container
        row_id, row_name, row_zoom = result
        return ComputationDetail(name=row_name, zoom_level=int(row_zoom), id = int(row_id))


def _get_polygon_detail(conn: psycopg.Connection, name: str) -> tuple:
    query = """
           SELECT polygon_id, name, raw_geometry 
           FROM polygon_master 
           WHERE name = %s;
    """
   
    with conn.cursor() as cur:
        cur.execute(query, (name,))
        result = cur.fetchone()
        if not result:
            logger.error(f"polygon with name '{name}' not found.")
            raise ValueError(f"polygon '{name}' does not exist.")
        row_id, row_name, row_geometry = result 
        return PolygonDetail(id=int(row_id), geometry=row_geometry, name=row_name)


def _get_database_conn_string():
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


# ###########################
# Public methods 
# ---------------------------  

def link_polygon_to_computation(polygon_name: str, computation_name: str) -> int:

    logger = logging.getLogger("main." + __name__)
    db_conn_string = _get_database_conn_string()

    with psycopg.connect(db_conn_string) as conn:
        try:
            polygon_detail: PolygonDetail = _get_polygon_detail(conn, polygon_name)
            computation_detail: ComputationDetail = _get_computation_detail(conn, computation_name)

            logger.info(f"polygon id is {polygon_detail.id}")
            logger.info(f"computation id is {computation_detail.id}")
            logger.info(f"z-level is {computation_detail.zoom_level}")
             
            _create_polygon_computation(conn, polygon_detail.id, computation_detail.id)
            raw_coordinates = polygon_detail.geometry["coordinates"]
            coordinates = raw_coordinates[0]
            logger.info(f"polygon coordinates are {coordinates}")
            polygon_tiles = _get_intersecting_tiles(coordinates, computation_detail.zoom_level)

            for tile in polygon_tiles:
                logger.info(f"insert tile x: {tile.x}, y:{tile.y}, {tile.z}")
                tile_id = _create_geo_tile(conn, tile)
                _create_polygon_tile(conn, polygon_detail.id, tile_id, tile.area_fraction)
                _create_computation_tile(conn, computation_detail.id, tile_id)
                
            conn.commit()
        except Exception as e:
            conn.rollback()
            logger.exception(f"error happened. database changes are rolled back.")
            raise e 
            
    return 


def add_computation(computation_name, zoom_level):
    db_conn_string = _get_database_conn_string()
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
        raise ValueError(f"Unsupported geometry type: {geom_type}. Expected Polygon or MultiPolygon.")

    db_conn_string = _get_database_conn_string()
    try:
        polygon_id = _store_polygon_in_database(db_conn_string, polygon_name, geometry) 
        logger.info("polygon inserted with id: " + str(polygon_id))
    except UniqueViolation:
        logger.info("A polygon with the name {0} already exists!".format(polygon_name))


def show_polygon_tiles(file_name, zoom_level):

    file_path = Path(file_name)
    if not file_path.exists() or not file_path.is_file():
        logger.error(f"Target GeoJSON source path does not exist: {file_path}")
        raise FileNotFoundError(f"Missing file: {file_path}")

    with file_path.open("r", encoding="utf-8") as file:
        geometry = json.load(file)
    
    # 3. Validate geometry type and extract coordinates
    geom_type = geometry.get("type")
    if geom_type not in ["Polygon", "MultiPolygon"]:
        raise ValueError(f"Unsupported geometry type: {geom_type}. Expected Polygon or MultiPolygon.")

    raw_coordinates = geometry["coordinates"]
    coordinates = raw_coordinates[0]
    tiles = _get_intersecting_tiles(coordinates, zoom_level)
    for tile in tiles:
        print(f"x: {tile.x}, y: {tile.y}, z: {tile.z}, fraction: {tile.area_fraction}")

    

def start_worker():
    print(f"start geo polygon process under PID: {os.getpid()}...")
    AppConfig.load()
    log_config = get_logger_config("global")
    AppConfig.init_logging(log_file=log_config.log_file, log_level=log_config.log_level)
    logger.info(f"geo polygon log config loaded...")
    # add_geo_polygon("polygon.json", "patna_zoo")
    # add_computation("NDVI", 16)
    # link_polygon_to_computation("patna_zoo", "NDVI")
    show_polygon_tiles("polygon.json", 14)


if __name__ == "__main__":
    start_worker()