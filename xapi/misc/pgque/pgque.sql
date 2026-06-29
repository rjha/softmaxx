
-- 
-- pgque installation guide 
-- https://github.com/NikolayS/PgQue
-- 


-- 1. create a schema in your database 
CREATE SCHEMA IF NOT EXISTS pgque;

/* 
INSTALL 
-- clone the repo and run command 
$PAGER=cat psql --no-psqlrc --single-transaction -d xapi_db \ 
    --command="SET search_path TO pgque;" -f sql/pgque.sql

UNINSTALL 
$psql --no-psqlrc --single-transaction -d xapi_db -f sql/pgque_uninstall.sql
*/

-- after installation, we want to grant rights to our 
-- JDBC xapi_user to pgque schema. 
-- log in as postgres user (IMP!!!) to run commands 
-- $psql -U postgres -d xapi_db


-- 1. Switch context to your target database instance
\c xapi_db 

-- 2. Ensure xapi_user can see the schema namespace
GRANT USAGE ON SCHEMA pgque TO xapi_user;

-- 
-- grant the built-in PgQue roles to xapi_user
--  Explicitly granting execution rights to functions, tables,
-- and sequences is not recommened. 

GRANT pgque_reader TO xapi_user;
GRANT pgque_writer TO xapi_user;
GRANT pgque_admin to xapi_user;

-- create a tube (event queue) and register a consumer 
SELECT pgque.create_queue('xapi_tube');
SELECT pgque.register_consumer('xapi_tube', 'xapi_tube_worker' );

-- modern alias for register_csonsumer 
SELECT pgque.subscribe('xapi_tube', 'xapi_tube_worker');

-- 
-- switch to xapi_user
-- psql -U xapi_user -d xapi_db
-- 

select pgque.send('xapi_tube', 'payload:token07:token08');
-- return value is the event_id 
-- receive nothing!!! 

select * from pgque.receive('xapi_tube', 'xapi_tube_worker', 10);
-- select pgque.ack(1);


-- 
-- DEBUG 00: force tick + run ticker
-- receive a batch and ACK it 
-- 

-- select pgque.force_next_tick('xapi_tube');
-- select pgque.ticker();



-- 
-- DEBUG 01:
-- peek into in a queue 
-- find event_table_id 
-- 
-- 

SELECT queue_cur_table
FROM pgque.queue
WHERE queue_name = 'xapi_tube';

-- queue_cur_table;

-- 
--  content of event table 
-- This code will return the event_id, e.g. 1, 
-- Then event_ table will be event_1 below 
--  
SELECT ev_id, 
ev_time, 
ev_txid,
ev_owner,
ev_retry,
ev_type, 
ev_data 
FROM pgque.event_1 --
ORDER BY ev_id DESC 
LIMIT 10;


-- 
-- DEBUG 02: subscriber data with active_batch_id 
-- You can also see last_tick and next_tick
-- 

SELECT
    sub_id,
    sub_queue,
    sub_consumer,
    sub_batch AS active_batch_id,
    sub_active,
    sub_last_tick,
    sub_next_tick,
    sub_role
FROM pgque.subscription;


-- 
-- DEBUG 03: tick snapshot 
-- 

 SELECT
    tick_id,
    tick_queue,         
    tick_time,
    tick_snapshot,
    tick_event_seq
FROM pgque.tick
ORDER BY tick_id DESC
LIMIT 20;


--
-- DEBUG 04: predict the content of receive()
-- START. step 1 => get event table id
select queue_id,
    queue_name, 
    queue_cur_table, 
    queue_event_seq, 
    queue_tick_seq 
FROM pgque.queue
WHERE queue_name = 'xapi_tube';


-- step 2. get subscriber_queue_id 

SELECT
    sub_id,
    sub_queue,
    sub_consumer,
    sub_active,
    sub_last_tick,
    sub_batch 
    sub_next_tick,
    sub_role
FROM pgque.subscription;

-- 
-- step3. use event_table_id and subscriber_id 
-- to find current snapshot rows (content of receive())
-- as well as committed rows that are not part of the snapshot 
-- 
--

SELECT 
    e.ev_id,
    e.ev_type,
    e.ev_time,
    e.ev_data
FROM pgque.event_1 e  
JOIN pgque.subscription s ON s.sub_queue = 1 AND s.sub_consumer = 1
JOIN pgque.tick t ON t.tick_id = s.sub_last_tick AND t.tick_queue = s.sub_queue
WHERE 
  -- 1. Discard rows already consumed inside your older subscription boundary
  NOT pg_visible_in_snapshot(e.ev_txid, t.tick_snapshot)
  -- 2. Include rows that have fully completed and committed up to this exact millisecond
  AND pg_visible_in_snapshot(e.ev_txid, pg_current_snapshot())
ORDER BY e.ev_id ASC;


-- next batch_id sequence 
SELECT last_value AS last_allocated_batch_id
FROM pgque.batch_id_seq;


-- 
-- DEBUG 05: 
-- simulate handler errors using NACK 
-- 1. set max_retries to 2
-- 

select pgque.set_queue_config('xapi_tube', 'max_retries', '2');


-- 
-- on error, we can set NACK for a message. after setting NACK also, 
-- all the messages of the batch will still appear. 
-- We need to send an ACK for the batch. After sending ACK, the cursor will 
-- advance. 
-- 
-- To put the NACK messages into the main queue, we need to run 
-- the maintenance step, /> select pgque.maint_retry_events();
-- The NACK message will appear after sending a new tick now. 
--  
-- 
-- The NACK message would be visible when,
--  
-- a. current batch has received  ACK  - so cursor can advance. 
-- b. maint_retry_events() has been called 
-- c. the retry period has elapsed
-- d. A new tick has been sent, so a new batch can be prepared 
-- 
-- Here we NACK oldest message  of the batch 
-- heck which message will be NACK'ed by running this query separately  
--  select * from  pgque.receive('xapi_tube', 'xapi_tube_worker', 1) LIMIT 1;
--

do $$
declare
    v_msg pgque.message;
begin
    select * into v_msg from pgque.receive('xapi_tube', 'xapi_tube_worker', 1) ORDER BY MSG_ID  LIMIT 1;
    perform pgque.nack(v_msg.batch_id, v_msg, '60 seconds'::interval, 'simulated failure');
    -- perform pgque.ack(v_msg.batch_id);
end $$;


-- 
-- send NACK for a particular MSG_ID 
-- 

do $$
declare
    v_msg pgque.message;
begin
    -- 1. Fetch up to 10 messages from the active batch
    SELECT * INTO v_msg 
    FROM pgque.receive('xapi_tube', 'xapi_tube_worker', 10) 
    WHERE msg_id = 36048; 

    -- 2. Only perform NACK if the message was actually found in the batch
    IF v_msg.msg_id IS NOT NULL THEN
        PERFORM pgque.nack(v_msg.batch_id, v_msg, '60 seconds'::interval, 'simulated failure');
        -- Optional: ACK the rest of the batch if you are done processing it
        -- PERFORM pgque.ack(v_msg.batch_id);
    ELSE
        RAISE NOTICE 'Message ID was not found inside the currently active batch.';
    END IF;
end $$;

-- 
-- after NACK, send ACK to advance the cursor,
-- /> select pgque.ack(batch_id);
-- /> select pgque.maint_retry_events();
-- send a new tick() 
-- 




-- 
-- DEBUG 06: content of dead letter queue 
-- 


SELECT 
    ev_id,
    ev_type,
    ev_time,
    ev_data, 
    dl_reason,
    dl_time, 
    dl_queue_id,
    dl_consumer_id
FROM pgque.dead_letter
ORDER BY ev_time DESC;


-- 
-- DEBUG 07
-- Find events between 2 ticks
-- get tick_id using DEBUG03 
-- 
-- 

SELECT 
    e.ev_id,
    e.ev_type,
    e.ev_time,
    e.ev_data
FROM pgque.event_1 e -- Replace with your current live partition table
JOIN pgque.tick t_start ON t_start.tick_id = 6 AND t_start.tick_queue = 1 -- Lower Tick Boundary
JOIN pgque.tick t_end   ON t_end.tick_id = 61   AND t_end.tick_queue = 1   -- Upper Tick Boundary
WHERE 
  -- 1. Exclude events that were already visible at the start tick
  NOT pg_visible_in_snapshot(e.ev_txid, t_start.tick_snapshot)
  -- 2. Include only events that became visible by the end tick
  AND pg_visible_in_snapshot(e.ev_txid, t_end.tick_snapshot)
ORDER BY e.ev_id ASC;

-- 
-- DEBUG 08 
-- if events have received ACK or not 
-- 

SELECT 
    e.ev_id,
    e.ev_time,
    -- If true, the consumer has already processed and ACKed this specific row
    pg_visible_in_snapshot(e.ev_txid, t.tick_snapshot) AS is_acknowledged,
    s.sub_consumer AS consumer_id
FROM pgque.event_1 e -- Replace with your current live partition table
JOIN pgque.subscription s ON s.sub_queue = 1 AND s.sub_consumer = 1
JOIN pgque.tick t ON t.tick_id = s.sub_last_tick AND t.tick_queue = s.sub_queue
WHERE e.ev_id IN (36034, 36035, 36036);



-- 
-- DEBUG 09
-- individual NACK message 
-- 


select pgque.nack(
    4, 
    ROW(
        6008, 
        4, 
        'default', 
        'payload:token05:token06',
        NULL,
        '2026-06-25 21:59:25.550974+05:30',
        NULL,
        NULL,
        NULL,
        NULL)::pgque.message,
    '30 seconds'::interval, 
    'simulated failure');
