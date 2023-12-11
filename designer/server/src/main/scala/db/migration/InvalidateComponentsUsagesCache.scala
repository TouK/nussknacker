package db.migration

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.ui.db.NuTables
import pl.touk.nussknacker.ui.db.migration.SlickMigration
import pl.touk.nussknacker.ui.process.repository.ScenarioComponentsUsagesHelper

trait InvalidateComponentsUsagesCache extends SlickMigration with NuTables with LazyLogging {

  import profile.api._
  import slick.dbio.DBIOAction

  import scala.concurrent.ExecutionContext.Implicits.global

  override def migrateActions: DBIOAction[Seq[Int], NoStream, Effect.Read with Effect.Read with Effect.Write] = {
    for {
      allVersionIds <- processVersionsTableWithUnit.map(pve => (pve.id, pve.processId)).result
      updated <- DBIOAction.sequence(allVersionIds.zipWithIndex.map { case ((id, processId), scenarioIndex) =>
        updateOne(id, processId, scenarioIndex + 1, scenariosCount = allVersionIds.size)
      })
    } yield updated
  }

  private def updateOne(
      id: VersionId,
      processId: ProcessId,
      scenarioNo: Int,
      scenariosCount: Int
  ): DBIOAction[Int, NoStream, Effect.Read with Effect.Write] = {
    for {
      scenarioJson <- processVersionsTable.filter(v => v.id === id && v.processId === processId).map(_.json).result.head
      updatedComponentsUsages <- processVersionsTable
        .filter(v => v.id === id && v.processId === processId)
        .map(_.componentsUsages)
        .update {
          logger.trace("Migrate scenario ({}/{}), id: {}, version id: {}", scenarioNo, scenariosCount, processId, id)
          updateComponentsUsages(scenarioJson)
        }
    } yield updatedComponentsUsages
  }

  private def updateComponentsUsages(scenarioJson: String): String = {
    import io.circe.syntax._
    import pl.touk.nussknacker.ui.db.entity.ScenarioComponentsUsagesJsonCodec._

    val scenario         = ProcessMarshaller.fromJsonUnsafe(scenarioJson)
    val componentsUsages = ScenarioComponentsUsagesHelper.compute(scenario)
    componentsUsages.asJson.noSpaces
  }

}
