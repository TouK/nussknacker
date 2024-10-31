package db.migration.hsql

import db.migration.V1_058__UpdateAndAddMissingScenarioActivitiesDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_058__UpdateAndAddMissingScenarioActivities extends V1_058__UpdateAndAddMissingScenarioActivitiesDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
