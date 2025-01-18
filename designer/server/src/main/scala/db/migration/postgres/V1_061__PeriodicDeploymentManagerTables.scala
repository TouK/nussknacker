package db.migration.postgres

import db.migration.V1_061__PeriodicDeploymentManagerTablesDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_061__PeriodicDeploymentManagerTables extends V1_061__PeriodicDeploymentManagerTablesDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
