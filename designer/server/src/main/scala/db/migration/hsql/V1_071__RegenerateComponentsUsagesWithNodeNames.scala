package db.migration.hsql

import pl.touk.nussknacker.ui.db.migration.SlickMigration
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.HsqldbProfile

// Noop: regeneration was needed after introducing node ids into componentsUsages, but this branch doesn't have node ids yet (unlike staging), so there's nothing to regenerate.
class V1_071__RegenerateComponentsUsagesWithNodeNames extends SlickMigration {
  override protected lazy val profile = createNuJdbcProfileFrom(HsqldbProfile)

  override protected def migrateActions: DBIOAction[Any, NoStream, Effect] = DBIOAction.successful(())
}
