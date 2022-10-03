package db.migration.postgres

import db.migration.{V1_030__SpillStateToDisk => V1_030__SpillStateToDiskDefinition}
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_030__SpillStateToDisk extends V1_030__SpillStateToDiskDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}