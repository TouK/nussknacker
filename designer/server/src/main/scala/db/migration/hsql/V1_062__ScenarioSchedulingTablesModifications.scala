package db.migration.hsql

import db.migration.V1_062__ScenarioSchedulingTablesModificationsDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_062__ScenarioSchedulingTablesModifications extends V1_062__ScenarioSchedulingTablesModificationsDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
