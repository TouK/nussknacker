package utils

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.TableDrivenPropertyChecks._

class SchemaInSqlDetectorSpec extends AnyFunSuite {

  private val testCases = Table(
    ("sqlStatement", "missingRequiredSchema"),

    // statements with schema specified
    ("""DELETE FROM my_schema.users WHERE id = 1""", false),
    ("""INSERT INTO my_schema."orders" (id, name) VALUES (1, 'test')""", false),
    ("""ALTER TABLE "my_schema"."orders" ADD COLUMN status TEXT""", false),
    (
      """CREATE FUNCTION "my_schema".generate_uuid() RETURNS UUID AS $$
        BEGIN
          RETURN gen_random_uuid();
        END
        $$ LANGUAGE plpgsql;""",
      false
    ),
    ("""SET search_path TO my_schema""", false),
    ("""CREATE TABLE my_schema.users (id SERIAL PRIMARY KEY, name TEXT)""", false),
    ("""CREATE INDEX my_schema.idx_users_name ON my_schema.users (name)""", false),
    ("""CREATE SEQUENCE my_schema.user_seq""", false),
    ("""CREATE VIEW my_schema.active_users AS SELECT * FROM my_schema.users WHERE active = true""", false),
    ("""CREATE MATERIALIZED VIEW my_schema.reports AS SELECT * FROM my_schema.orders""", false),
    (
      """CREATE PROCEDURE my_schema.process_data() AS $$
        BEGIN
          UPDATE my_schema.orders SET status = 'processed';
        END
        $$ LANGUAGE plpgsql;""",
      false
    ),
    ("""ALTER FUNCTION my_schema.calculate_total() SET SCHEMA new_schema""", false),
    ("""DROP TABLE my_schema.users""", false),
    ("""DROP VIEW my_schema.reports""", false),
    ("""GRANT SELECT ON my_schema.users TO app_user""", false),
    ("""SELECT * FROM information_schema.tables WHERE table_schema = 'my_schema'""", false),
    ("""SELECT * FROM pg_catalog.pg_tables WHERE schemaname = 'my_schema'""", false),
    (
      """ALTER TABLE "arg"."sticky_notes" ADD CONSTRAINT "sticky_notes_scenario_version_fk" FOREIGN KEY ("scenario_id", "scenario_version_id") REFERENCES "arg"."process_versions" ("process_id", "id") ON DELETE CASCADE;""",
      false
    ),
    (
      """CREATE OR REPLACE FUNCTION "${profile.schemaName}".generate_random_uuid() RETURNS UUID AS 'BEGIN RETURN uuid_in(overlay(overlay(md5(random()::text || '':'' || random()::text) placing ''4'' from 13) placing to_hex(floor(random() * (11 - 8 + 1) + 8)::int)::text from 17)::cstring);END' LANGUAGE plpgsql;""",
      false
    ),
    (
      """INSERT INTO #arg.distributed_locks AS dl (name, lock_until, locked_at, locked_by)
             VALUES (
               arg,
               LOCALTIMESTAMP + INTERVAL 'arg milliseconds',
               LOCALTIMESTAMP,
               arg
             )
             ON CONFLICT (name) DO UPDATE SET
               lock_until = EXCLUDED.lock_until,
               locked_at  = LOCALTIMESTAMP,
               locked_by  = EXCLUDED.locked_by
             WHERE dl.lock_until < LOCALTIMESTAMP
                OR dl.locked_by = arg""",
      false
    ),
    (
      """UPDATE #arg.distributed_locks
             SET lock_until = LOCALTIMESTAMP
             WHERE name = arg AND locked_by = arg""",
      false
    ),

    // statements with NO required schema specified
    ("""DELETE FROM users WHERE id = 1""", true),
    ("""INSERT INTO orders (id, name) VALUES (1, 'test')""", true),
    ("""ALTER TABLE orders ADD COLUMN status TEXT""", true),
    (
      """CREATE FUNCTION generate_uuid() RETURNS UUID AS $$
        BEGIN
          RETURN gen_random_uuid();
        END
        $$ LANGUAGE plpgsql;""",
      true
    ),
    (
      """ALTER TABLE "sticky_notes" ADD CONSTRAINT fk_scenario
        FOREIGN KEY (scenario_id)
        REFERENCES process_versions (process_id)
        ON DELETE CASCADE;""",
      true
    ),
    (
      """ALTER TABLE my_schema."sticky_notes" ADD CONSTRAINT fk_scenario
        FOREIGN KEY (scenario_id)
        REFERENCES process_versions (process_id)
        ON DELETE CASCADE;""",
      true
    ),
    ("""CREATE TABLE users (id SERIAL PRIMARY KEY, name TEXT)""", true),
    ("""CREATE INDEX idx_users_name ON users (name)""", true),
    ("""CREATE SEQUENCE user_seq""", true),
    ("""CREATE VIEW active_users AS SELECT * FROM users WHERE active = true""", true),
    ("""CREATE MATERIALIZED VIEW reports AS SELECT * FROM orders""", true),
    (
      """CREATE PROCEDURE process_data() AS $$
        BEGIN
          UPDATE orders SET status = 'processed';
        END
        $$ LANGUAGE plpgsql;""",
      true
    ),
    ("""ALTER FUNCTION calculate_total() SET SCHEMA new_schema""", true),
    ("""DROP TABLE users""", true),
    ("""DROP VIEW reports""", true),
    ("""GRANT SELECT ON users TO app_user""", true),
    ("""SELECT * FROM tables WHERE table_schema = 'public'""", true),
    ("""SELECT * FROM pg_tables WHERE schemaname = 'public'""", true),
    (
      """ALTER TABLE "${profile.schemaName}"."sticky_notes" ADD CONSTRAINT "sticky_notes_scenario_version_fk" FOREIGN KEY ("scenario_id", "scenario_version_id") REFERENCES "process_versions" ("process_id", "id") ON DELETE CASCADE;""",
      true
    )
  )

  test(s"SchemaInSqlDetector should correctly detect missing schemas") {
    forAll(testCases) { (sqlStatement, expectedResult) =>
      assert(
        SchemaInSqlDetector.isMissingRequiredSchema(sqlStatement) == expectedResult,
        s"Failed for SQL: $sqlStatement"
      )
    }
  }

}
