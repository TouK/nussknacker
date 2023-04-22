package db.migration

import io.circe._
import pl.touk.nussknacker.ui.db.EspTables
import pl.touk.nussknacker.ui.db.migration.SlickMigration

trait V1_037__ComponentsUsagesDefinition extends SlickMigration with EspTables {

  import profile.api._
  import slick.dbio.DBIOAction
  import scala.concurrent.ExecutionContext.Implicits.global

  override def migrateActions: profile.api.DBIOAction[Any, profile.api.NoStream, _ <: profile.api.Effect] = {
    for {
      latestVersionIds <- processVersionsTableWithScenarioJson
        .groupBy(_.processId)
        .map { case (processId, versions) => (processId, versions.map(_.id).max) }
        .result
    } yield ???
  }

}

object V1_037__ComponentsUsagesDefinition {

  private val legacyProperty = "path"
  private val newProperty = "slug"

  private[migration] def componentsUsages(jsonProcess: Json): Json = {
    ???
  }

}
