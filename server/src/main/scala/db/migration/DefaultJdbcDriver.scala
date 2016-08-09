package db.migration

import slick.driver.{HsqldbDriver, JdbcDriver}

object DefaultJdbcDriver {

  val driver: JdbcDriver = HsqldbDriver

}
