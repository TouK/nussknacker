package db.migration.hsql

import db.migration.V1_039__FillComponentsUsagesDefinition
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_047__RegenerateComponentsUsages extends V1_039__FillComponentsUsagesDefinition {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
