package db.migration.hsql

import db.migration.{V1_030__SpillStateToDisk => V1_030__SpillStateToDiskDefinition}
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_030__SpillStateToDisk extends V1_030__SpillStateToDiskDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}