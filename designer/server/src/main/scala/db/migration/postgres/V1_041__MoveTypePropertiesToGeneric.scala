package db.migration.postgres

import db.migration.V1_041__MoveTypePropertiesToGenericDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_041__MoveTypePropertiesToGeneric extends V1_041__MoveTypePropertiesToGenericDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
