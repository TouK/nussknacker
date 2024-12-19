package db.migration.postgres

import db.migration.V1_060__PeriodicDeploymentManagerTablesDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_060__PeriodicDeploymentManagerTables extends V1_060__PeriodicDeploymentManagerTablesDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
