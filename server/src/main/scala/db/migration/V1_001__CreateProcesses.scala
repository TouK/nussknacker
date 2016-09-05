package db.migration

import pl.touk.esp.ui.db.migration.CreateProcessesMigration
import slick.jdbc.JdbcProfile

class V1_001__CreateProcesses extends CreateProcessesMigration {
  override protected val profile: JdbcProfile = DefaultJdbcProfile.profile
}
