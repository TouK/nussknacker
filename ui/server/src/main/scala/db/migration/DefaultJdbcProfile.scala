package db.migration

import slick.jdbc.{HsqldbProfile, JdbcProfile}

object DefaultJdbcProfile {

  val profile: JdbcProfile = HsqldbProfile

}
