package db.migration.postgres

import db.migration.{V1_031__FragmentSpecificData => V1_031__FragmentSpecificDataDefinition}
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_031__FragmentSpecificData extends V1_031__FragmentSpecificDataDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}