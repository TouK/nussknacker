package db.migration.postgres

import db.migration.InvalidateComponentsUsagesCache
import slick.jdbc.PostgresProfile

class V1_071__RegenerateComponentsUsagesWithNodeNames extends InvalidateComponentsUsagesCache {
  override protected lazy val profile = createNuJdbcProfileFrom(PostgresProfile)
}
