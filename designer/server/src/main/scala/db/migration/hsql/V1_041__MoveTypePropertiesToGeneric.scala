package db.migration.hsql

import db.migration.V1_041__MoveTypePropertiesToGenericDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_041__MoveTypePropertiesToGeneric extends V1_041__MoveTypePropertiesToGenericDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
