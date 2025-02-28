package db.migration.hsql

import db.migration.InvalidateComponentsUsagesCache
import slick.jdbc.HsqldbProfile

class V1_039__FillComponentsUsages extends InvalidateComponentsUsagesCache {
  override protected lazy val profile = createProfileWithSchema(HsqldbProfile)
}
