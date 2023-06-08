package db.migration.postgres

import db.migration.V1_042__RenameSubprocessToFragmentDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_042__RenameSubprocessToFragment extends V1_042__RenameSubprocessToFragmentDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
