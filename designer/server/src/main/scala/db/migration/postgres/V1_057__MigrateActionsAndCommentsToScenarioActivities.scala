package db.migration.postgres

import db.migration.V1_057__MigrateActionsAndCommentsToScenarioActivitiesDefinition
import slick.jdbc.PostgresProfile

class V1_057__MigrateActionsAndCommentsToScenarioActivities
    extends V1_057__MigrateActionsAndCommentsToScenarioActivitiesDefinition {
  override protected lazy val profile = createNuJdbcProfileFrom(PostgresProfile)

  import profile.apiWithEnforcedSchema._

  override protected def createGenerateRandomUuidFunction(): DBIOAction[Int, NoStream, Effect.All] = {
    sqlu"""CREATE OR REPLACE FUNCTION "#${profile.schemaName}".generate_random_uuid() RETURNS UUID AS 'BEGIN RETURN uuid_in(overlay(overlay(md5(random()::text || '':'' || random()::text) placing ''4'' from 13) placing to_hex(floor(random() * (11 - 8 + 1) + 8)::int)::text from 17)::cstring);END' LANGUAGE plpgsql;"""
  }

}
