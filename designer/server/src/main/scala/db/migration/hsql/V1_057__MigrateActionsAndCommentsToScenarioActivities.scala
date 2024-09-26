package db.migration.hsql

import db.migration.V1_057__MigrateActionsAndCommentsToScenarioActivitiesDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_057__MigrateActionsAndCommentsToScenarioActivities
    extends V1_057__MigrateActionsAndCommentsToScenarioActivitiesDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile

  import profile.api._

  override protected def createGenerateRandomUuidFunction(): DBIOAction[Int, NoStream, Effect.All] = {
    sqlu"""CREATE FUNCTION generate_random_uuid() RETURNS UUID DETERMINISTIC RETURN UUID();"""
  }

}
