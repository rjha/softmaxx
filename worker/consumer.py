import time
import json
import logging
from pgque import connect, Message


# Configuration
DSN = "postgresql://xxxx_user:xxxx_password@localhost:5432/xapi_db"
QUEUE_NAME = "xapi_tube"
CONSUMER_NAME = "xapi_tube_worker"
BATCH_SIZE = 100
POLL_INTERVAL_SECONDS = 1.0

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

def process_event(msg: Message):
    """
    Your custom business logic for processing Java producer events.
    Modify this function based on the event structure sent by Java.
    """
    logging.info(f"Processing event ID: {msg.msg_id} | Type: {msg.type}")
    
    # Payload is typically parsed or can be treated as a dict/string depending on Java serializer
    payload = msg.payload
    print(payload)

    if isinstance(payload, str):
        try:
            payload = json.loads(payload)
        except json.JSONDecodeError:
            pass
            
    # Example processing logic
    # print(f"Payload Data: {payload}")

def run_consumer():
    logging.info(f"Starting consumer '{CONSUMER_NAME}' on queue '{QUEUE_NAME}'...")
    
    # Establish a connection context with PgQue
    with connect(DSN) as client:
        
        # Explicitly register the consumer if it hasn't been done via SQL yet
        # PgQue allows safe, idempotent registration.
        try:
            with client.conn.cursor() as cur:
                cur.execute("SELECT pgque.register_consumer(%s, %s)", (QUEUE_NAME, CONSUMER_NAME))
            client.conn.commit()
        except Exception as e:
            client.conn.rollback() # Consumer might already be registered
            
        # Main worker poll loop
        while True:
            try:
                # 1. Fetch a snapshot batch of events
                messages = client.receive(QUEUE_NAME, CONSUMER_NAME, BATCH_SIZE)
                
                if not messages:
                    # No new events available; sleep to respect the pg_cron tick window (~100ms-1s)
                    time.sleep(POLL_INTERVAL_SECONDS)
                    continue
                
                logging.info(f"Received batch of {len(messages)} messages.")
                batch_id = messages[0].batch_id
                batch_failed = False
                
                # 2. Iterate and process messages sequentially or in parallel
                for msg in messages:
                    try:
                        process_event(msg)
                    except Exception as exc:
                        logging.error(f"Error processing message {msg.msg_id}: {exc}")
                        batch_failed = True
                        break # Break batch loop to trigger negative acknowledgement
                
                # 3. Finalize the snapshot batch boundary
                if batch_failed:
                    # Rejects the batch, returning it to retry queue with an explicit 30s backoff delay
                    logging.warning(f"Nacking batch {batch_id} due to processing failure.")
                    client.nack(batch_id, retry_delay=30)
                else:
                    # Success: Advances the consumer's log cursor past this batch
                    logging.info(f"Acking batch {batch_id} successfully.")
                    client.ack(batch_id)
                
                # Commit the ack/nack transaction to Postgres
                client.conn.commit()
                
            except KeyboardInterrupt:
                logging.info("Shutting down consumer gracefully...")
                break
            except Exception as loop_err:
                logging.error(f"Unexpected loop tracking error: {loop_err}")
                # Ensure we don't leave dangling uncommitted transactions
                try:
                    client.conn.rollback()
                except Exception:
                    pass
                time.sleep(5) # Cooldown on database connection drops or network glitches

if __name__ == "__main__":
    run_consumer()

