package db.migration.postgres

import db.migration.{V1_029__AdditionalBranchesChange => V1_029__AdditionalBranchesChangeDefinition}
import slick.jdbc.{HsqldbProfile, JdbcProfile, PostgresProfile}

class V1_029__AdditionalBranchesChange extends V1_029__AdditionalBranchesChangeDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
