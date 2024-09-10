package db.migration.hsql

import db.migration.V1_056__MigrateActionsAndCommentsToScenarioActivitiesDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_056__MigrateActionsAndCommentsToScenarioActivities
    extends V1_056__MigrateActionsAndCommentsToScenarioActivitiesDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
