package db.migration.postgres

import db.migration.V1_043__RenameSubprocessToFragmentDefinition
import slick.jdbc.PostgresProfile

class V1_043__RenameSubprocessToFragment extends V1_043__RenameSubprocessToFragmentDefinition {
  override protected lazy val profile = createNuJdbcProfileFrom(PostgresProfile)
}
