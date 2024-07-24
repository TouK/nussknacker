package db.migration.postgres

import db.migration.V1_054__FragmentAddComponentGroupPropertyDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_054__FragmentAddComponentGroupProperty extends V1_054__FragmentAddComponentGroupPropertyDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
