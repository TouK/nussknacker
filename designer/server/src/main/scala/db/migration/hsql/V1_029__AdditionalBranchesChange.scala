package db.migration.hsql

import db.migration.{V1_029__AdditionalBranchesChange => V1_029__AdditionalBranchesChangeDefinition}
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_029__AdditionalBranchesChange extends V1_029__AdditionalBranchesChangeDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
