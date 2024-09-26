package db.migration.hsql

import db.migration.V1_056__CreateScenarioActivitiesDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_056__CreateScenarioActivities extends V1_056__CreateScenarioActivitiesDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
