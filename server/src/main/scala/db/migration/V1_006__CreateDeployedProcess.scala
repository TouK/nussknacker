package db.migration

import pl.touk.esp.ui.db.migration.CreateDeployedProcessesMigration
import slick.jdbc.JdbcProfile

class V1_006__CreateDeployedProcess extends CreateDeployedProcessesMigration {
  override protected val profile: JdbcProfile = DefaultJdbcProfile.profile
}
