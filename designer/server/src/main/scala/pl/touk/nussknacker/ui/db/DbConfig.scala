package pl.touk.nussknacker.ui.db

import slick.jdbc.{JdbcBackend, JdbcProfile}

class DbConfig(val db: JdbcBackend.Database, val profile: JdbcProfile)
object DbConfig {
  def apply(db: JdbcBackend.Database, profile: JdbcProfile): DbConfig = new DbConfig(db, profile)
}