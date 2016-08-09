package db.migration

import pl.touk.esp.ui.db.migration.CreateTagsMigration
import slick.driver.JdbcDriver

class V1_002__CreateTags extends CreateTagsMigration {
  override protected val driver: JdbcDriver = DefaultJdbcDriver.driver
}
