package db.migration.postgres

import db.migration.V1_046__RenameSubprocessToFragmentDefinition
import slick.jdbc.PostgresProfile

class V1_046__RenameSubprocessToFragment extends V1_046__RenameSubprocessToFragmentDefinition {
  override protected lazy val profile = createProfileWithSchema(PostgresProfile)
}
