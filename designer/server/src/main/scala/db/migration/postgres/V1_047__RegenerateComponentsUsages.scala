package db.migration.postgres

import db.migration.V1_039__FillComponentsUsagesDefinition
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_047__RegenerateComponentsUsages extends V1_039__FillComponentsUsagesDefinition {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
