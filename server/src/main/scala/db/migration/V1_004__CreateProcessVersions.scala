package db.migration

import pl.touk.esp.ui.db.migration.CreateProcessVersionsMigration
import slick.jdbc.JdbcProfile

class V1_004__CreateProcessVersions extends CreateProcessVersionsMigration {
  override protected val profile: JdbcProfile = DefaultJdbcProfile.profile
}
