package db.migration

import java.sql.Connection

import org.flywaydb.core.api.migration.jdbc.JdbcMigration

class V1_004__CreateEnvironmentsMigration extends JdbcMigration {
  override def migrate(connection: Connection): Unit = {
    connection.prepareStatement(
      """
        |CREATE TABLE "environments" (
        |  "name" VARCHAR(254) NOT NULL PRIMARY KEY
        |)
      """.stripMargin).execute()

  }
}
