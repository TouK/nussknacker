package db.migration.postgres

import db.migration.InvalidateComponentsUsagesCache
import slick.jdbc.PostgresProfile

class V1_039__FillComponentsUsages extends InvalidateComponentsUsagesCache {
  override protected lazy val profile = createProfileWithSchema(PostgresProfile)
}
