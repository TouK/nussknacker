package db.migration.hsql

import db.migration.V1_041__RemoveTypeSpecificDataDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_041__RemoveTypeSpecificData extends V1_041__RemoveTypeSpecificDataDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
