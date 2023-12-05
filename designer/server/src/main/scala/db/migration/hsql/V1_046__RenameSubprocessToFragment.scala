package db.migration.hsql

import db.migration.V1_046__RenameSubprocessToFragmentDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_046__RenameSubprocessToFragment extends V1_046__RenameSubprocessToFragmentDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
