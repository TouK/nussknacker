package db.migration

import java.sql.Connection

import org.flywaydb.core.api.migration.jdbc.JdbcMigration

class V1_005__CreateDeployedProcess extends JdbcMigration {
  override def migrate(connection: Connection): Unit = {
    connection.prepareStatement(
      """
        |CREATE TABLE "deployed_process_versions" (
        |  "process_id"         VARCHAR2(254) NOT NULL,
        |  "process_version_id" NUMBER       NOT NULL,
        |  "environment"        VARCHAR2(254) NOT NULL,
        |  "user"               VARCHAR2(254) NOT NULL,
        |  "deploy_at"          TIMESTAMP    NOT NULL
        |)
      """.stripMargin).execute()
    connection.prepareStatement(
      """
        |ALTER TABLE "deployed_process_versions"
        |ADD CONSTRAINT "pk_deployed_process_version" PRIMARY KEY ("process_id", "process_version_id", "environment", "deploy_at")
      """.stripMargin).execute
    connection.prepareStatement(
      """
        |ALTER TABLE "deployed_process_versions"
        |ADD CONSTRAINT "env_in_deployed_proc_fk" FOREIGN KEY ("environment") REFERENCES "environments" ("name");
      """.stripMargin).execute
    connection.prepareStatement(
      """
        |ALTER TABLE "deployed_process_versions"
        |ADD CONSTRAINT "proc_ver_in_deployed_proc_fk" FOREIGN KEY ("process_id", "process_version_id") REFERENCES "process_versions" ("process_id", "id")
      """.stripMargin).execute
  }
}
