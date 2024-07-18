package db.migration.postgres

import db.migration.V1_054__FragmentAddIsDeprecatedPropertyDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_054__FragmentAddIsDeprecatedProperty extends V1_054__FragmentAddIsDeprecatedPropertyDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
