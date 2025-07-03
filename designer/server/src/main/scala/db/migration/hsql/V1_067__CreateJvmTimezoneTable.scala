package db.migration.hsql

import db.migration.{V1_061__PeriodicDeploymentManagerTablesDefinition, V1_067__CreateJvmTimezoneTableDefinition}
import slick.jdbc.HsqldbProfile

class V1_067__CreateJvmTimezoneTable extends V1_067__CreateJvmTimezoneTableDefinition {
  override protected lazy val profile = createNuJdbcProfileFrom(HsqldbProfile)
}
