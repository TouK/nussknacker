package db.migration

import pl.touk.esp.ui.db.migration.CreateProcessesMigration
import slick.driver.JdbcDriver

class V1_001__CreateProcesses extends CreateProcessesMigration {
  override protected val driver: JdbcDriver = DefaultJdbcDriver.driver
}
