package db.migration.postgres

import db.migration.V1_056__CreateScenarioActivitiesDefinition
import slick.jdbc.PostgresProfile

class V1_056__CreateScenarioActivities extends V1_056__CreateScenarioActivitiesDefinition {
  override protected lazy val profile = createNuJdbcProfileFrom(PostgresProfile)
}
