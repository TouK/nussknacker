package db.migration

import java.sql.Connection

import org.flywaydb.core.api.migration.jdbc.JdbcMigration

class V1_001__CreateProcesses extends JdbcMigration  {

  override def migrate(connection: Connection): Unit = {
    connection.prepareStatement(
      """
        |CREATE TABLE "processes" (
        |  "id"          VARCHAR2(254) NOT NULL PRIMARY KEY,
        |  "name"        VARCHAR2(254) NOT NULL,
        |  "description" VARCHAR2(1000),
        |  "type"        VARCHAR2(254) NOT NULL
        |)
      """.stripMargin).execute()
  }

}
