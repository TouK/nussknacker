package db.migration.postgres

import db.migration.V1_062__ScenarioSchedulingTablesModificationsDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_062__ScenarioSchedulingTablesModifications extends V1_062__ScenarioSchedulingTablesModificationsDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
