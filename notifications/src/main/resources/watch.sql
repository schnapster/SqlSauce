-- Based on: https://gist.github.com/bruth/6d53a3c2138c5adf53f5 - Many thanks!

-- Usage:
-- Create the functions of this file via migration / manually. Calling the sql of this file is repeatable.
-- Execute watch_table(table, 'channel') via migration / manually on each table for which you wish to receive changefeeds,
--  on a channel of your choice ("my_table_changefeed" for example)
-- Listen to the channels via NotificationService whenever your app starts, e.g. NotificationService#notif(ChangeFeedListener, "my_table_changefeed")
-- Notice the versioning that SqlSauce does in the payload. That is required to use the ChangeFeed notifications

-- You can look at one of the tests to get a feel on how to set up your DB and trigger and receive notifications.

-- Requires Postgres 9.4+

-- Check if a row or table has been modifed.
CREATE OR REPLACE FUNCTION if_modified_func()
  RETURNS TRIGGER AS $$
DECLARE
  channel     TEXT;
  payload     JSONB;
  rowdata_old JSONB;
  rowdata_new JSONB;
BEGIN
  IF TG_WHEN <> 'AFTER'
  THEN
    RAISE EXCEPTION 'if_modified_func() may only run as an AFTER trigger';
  END IF;

  -- Determine operation type
  IF (TG_OP = 'UPDATE' AND TG_LEVEL = 'ROW')
  THEN
    rowdata_old = row_to_json(OLD.*);
    rowdata_new = row_to_json(NEW.*);
  ELSIF (TG_OP = 'DELETE' AND TG_LEVEL = 'ROW')
    THEN
      rowdata_old = row_to_json(OLD.*);
      rowdata_new = NULL;
  ELSIF (TG_OP = 'INSERT' AND TG_LEVEL = 'ROW')
    THEN
      rowdata_old = NULL;
      rowdata_new = row_to_json(NEW.*);
  ELSIF NOT (TG_LEVEL = 'STATEMENT' AND TG_OP IN ('INSERT', 'UPDATE', 'DELETE', 'TRUNCATE'))
    THEN
      RAISE EXCEPTION '[if_modified_func] - Trigger func added as trigger for unhandled case: %, %', TG_OP, TG_LEVEL;
      RETURN NULL;
  END IF;

  -- Construct JSON payload
  payload = jsonb_build_object('schema_name', cast(TG_TABLE_SCHEMA AS TEXT),
                               'table_name', cast(TG_TABLE_NAME AS TEXT),
                               'operation', TG_OP,
                               'transaction_time', transaction_timestamp(),
                               'capture_time', clock_timestamp(),
                               'rowdata_old', rowdata_old,
                               'rowdata_new', rowdata_new,
                               'sqlsauce_notifications_version', 1);

  channel = TG_ARGV [0];

  -- Notify to channel with serialized JSON payload.
  PERFORM pg_notify(channel, cast(payload AS TEXT));

  RETURN NULL;
END;
$$
LANGUAGE plpgsql;

-- Create triggers that will execute on any change to the table.
CREATE OR REPLACE FUNCTION watch_table(target_table REGCLASS, channel TEXT)
  RETURNS VOID AS $$
DECLARE
  stmt TEXT;
BEGIN
  -- Drop existing triggers if they exist.
  EXECUTE unwatch_table(target_table);

  -- Row level watch trigger.
  stmt = 'CREATE TRIGGER watch_trigger_row AFTER INSERT OR UPDATE OR DELETE ON ' ||
         target_table || ' FOR EACH ROW EXECUTE PROCEDURE if_modified_func(' ||
         quote_literal(channel) || ');';
  RAISE NOTICE '%', stmt;
  EXECUTE stmt;

  -- Truncate level watch trigger. This will not contain any row data.
  stmt = 'CREATE TRIGGER watch_trigger_stmt AFTER TRUNCATE ON ' ||
         target_table || ' FOR EACH STATEMENT EXECUTE PROCEDURE if_modified_func(' ||
         quote_literal(channel) || ');';
  RAISE NOTICE '%', stmt;
  EXECUTE stmt;

END;
$$
LANGUAGE plpgsql;

-- Unwatch a table.
CREATE OR REPLACE FUNCTION unwatch_table(target_table REGCLASS)
  RETURNS VOID AS $$
BEGIN
  EXECUTE 'DROP TRIGGER IF EXISTS watch_trigger_row ON ' || target_table;
  EXECUTE 'DROP TRIGGER IF EXISTS watch_trigger_stmt ON ' || target_table;
END;
$$
LANGUAGE plpgsql;
