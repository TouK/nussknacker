package db.migration

import pl.touk.esp.ui.db.migration.CreateEnvironmentsMigration
import slick.jdbc.JdbcProfile

class V1_005__CreateEnvironmentsMigration extends CreateEnvironmentsMigration {
  override protected val profile: JdbcProfile = DefaultJdbcProfile.profile
}
