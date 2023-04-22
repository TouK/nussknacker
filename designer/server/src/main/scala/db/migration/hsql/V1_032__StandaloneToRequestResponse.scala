package db.migration.hsql

import db.migration.V1_032__StandaloneToRequestResponseDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_032__StandaloneToRequestResponse extends V1_032__StandaloneToRequestResponseDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
