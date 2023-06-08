package db.migration.hsql

import db.migration.V1_042__RenameSubprocessToFragmentDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_042__RenameSubprocessToFragment extends V1_042__RenameSubprocessToFragmentDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
