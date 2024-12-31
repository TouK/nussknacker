package db.migration.hsql

import db.migration.V1_060__PeriodicDeploymentManagerTablesDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_060__PeriodicDeploymentManagerTables extends V1_060__PeriodicDeploymentManagerTablesDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
