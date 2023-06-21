package db.migration.postgres

import db.migration.V1_041__RemoveTypeSpecificDataDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_041__RemoveTypeSpecificData extends V1_041__RemoveTypeSpecificDataDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
