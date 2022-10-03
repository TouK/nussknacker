package db.migration.hsql

import db.migration.{V1_031__FragmentSpecificData => V1_031__FragmentSpecificDataDefinition}
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_031__FragmentSpecificData extends V1_031__FragmentSpecificDataDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}