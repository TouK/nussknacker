package db.migration.postgres

import db.migration.V1_056__MigrateActionsAndCommentsToScenarioActivitiesDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_056__MigrateActionsAndCommentsToScenarioActivities
    extends V1_056__MigrateActionsAndCommentsToScenarioActivitiesDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
