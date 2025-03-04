package db.migration.hsql

import db.migration.V1_043__RenameSubprocessToFragmentDefinition
import slick.jdbc.HsqldbProfile

class V1_043__RenameSubprocessToFragment extends V1_043__RenameSubprocessToFragmentDefinition {
  override protected lazy val profile = createNuJdbcProfileFrom(HsqldbProfile)
}
