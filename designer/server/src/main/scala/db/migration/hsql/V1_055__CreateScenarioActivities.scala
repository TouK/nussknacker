package db.migration.hsql

import db.migration.V1_055__CreateScenarioActivitiesDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_055__CreateScenarioActivities extends V1_055__CreateScenarioActivitiesDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
