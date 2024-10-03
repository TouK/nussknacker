package db.migration.postgres

import db.migration.V1_058__UpdateAndAddMissingScenarioActivitiesDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_058__UpdateAndAddMissingScenarioActivities extends V1_058__UpdateAndAddMissingScenarioActivitiesDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
