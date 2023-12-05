package db.migration.hsql

import db.migration.InvalidateComponentsUsagesCache
import slick.jdbc.{HsqldbProfile, JdbcProfile}

class V1_047__RegenerateComponentsUsages extends InvalidateComponentsUsagesCache {
  override protected lazy val profile: JdbcProfile = HsqldbProfile
}
