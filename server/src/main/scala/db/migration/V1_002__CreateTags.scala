package db.migration

import java.sql.Connection

import org.flywaydb.core.api.migration.jdbc.JdbcMigration

class V1_002__CreateTags extends JdbcMigration {
  override def migrate(connection: Connection): Unit = {
    connection.prepareStatement(
      """
        |CREATE TABLE "tags" (
        |  "name"       VARCHAR2(254) NOT NULL,
        |  "process_id" VARCHAR2(254) NOT NULL
        |)
      """.stripMargin).execute()

    connection.prepareStatement(
      """
        |ALTER TABLE "tags" ADD CONSTRAINT "pk_tag" PRIMARY KEY ("name", "process_id")
      """.stripMargin).execute()

    connection.prepareStatement(
      """
        |ALTER TABLE "tags" ADD CONSTRAINT "tag-process-fk" FOREIGN KEY ("process_id") REFERENCES "processes" ("id")
      """.stripMargin).execute()


  }
}
