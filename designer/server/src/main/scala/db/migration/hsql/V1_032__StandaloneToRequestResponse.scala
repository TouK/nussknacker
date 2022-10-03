package db.migration.hsql

import db.migration.{V1_032__StandaloneToRequestResponseDefinition => V1_031__FragmentSpecificDataDefinition}
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_032__StandaloneToRequestResponse extends V1_031__FragmentSpecificDataDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}