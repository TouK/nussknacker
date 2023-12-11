package db.migration.postgres

import db.migration.InvalidateComponentsUsagesCache
import slick.jdbc.{JdbcProfile, PostgresProfile}

class V1_047__RegenerateComponentsUsages extends InvalidateComponentsUsagesCache {
  override protected lazy val profile: JdbcProfile = PostgresProfile
}
