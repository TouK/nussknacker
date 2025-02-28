package db.migration.hsql

import db.migration.V1_057__MigrateActionsAndCommentsToScenarioActivitiesDefinition
import slick.jdbc.HsqldbProfile

class V1_057__MigrateActionsAndCommentsToScenarioActivities
    extends V1_057__MigrateActionsAndCommentsToScenarioActivitiesDefinition {
  override protected lazy val profile = createProfileWithSchema(HsqldbProfile)

  import profile.jdbcProfile.api._

  override protected def createGenerateRandomUuidFunction(): DBIOAction[Int, NoStream, Effect.All] = {
    sqlu"""CREATE FUNCTION "${profile.schemaName}".generate_random_uuid() RETURNS UUID DETERMINISTIC RETURN UUID();"""
  }

}
