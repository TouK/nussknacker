package db.migration

import java.sql.Connection

import org.flywaydb.core.api.migration.jdbc.JdbcMigration

class V1_003__CreateProcessVersions extends JdbcMigration {
  override def migrate(connection: Connection): Unit = {
    connection.prepareStatement(
      """
        |CREATE TABLE "process_versions" (
        |  "id"          NUMBER       NOT NULL,
        |  "process_id"  VARCHAR2(254) NOT NULL,
        |  "json"        VARCHAR2(100000),
        |  "main_class"  VARCHAR2(5000),
        |  "create_date" TIMESTAMP    NOT NULL,
        |  "user"        VARCHAR2(254) NOT NULL
        |)
      """.stripMargin).execute()
    connection.prepareStatement(
      """
        |ALTER TABLE "process_versions" ADD CONSTRAINT "pk_process_version" PRIMARY KEY ("process_id", "id")
      """.stripMargin).execute()
  }
}
