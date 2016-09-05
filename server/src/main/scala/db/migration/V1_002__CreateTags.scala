package db.migration

import pl.touk.esp.ui.db.migration.CreateTagsMigration
import slick.jdbc.JdbcProfile

class V1_002__CreateTags extends CreateTagsMigration {
  override protected val profile: JdbcProfile = DefaultJdbcProfile.profile
}
