package db.migration.hsql

import db.migration.V1_061__PeriodicDeploymentManagerTablesDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_061__PeriodicDeploymentManagerTables extends V1_061__PeriodicDeploymentManagerTablesDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
