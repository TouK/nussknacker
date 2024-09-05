package db.migration.postgres

import db.migration.V1_055__CreateScenarioActivitiesDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_055__CreateScenarioActivities extends V1_055__CreateScenarioActivitiesDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
