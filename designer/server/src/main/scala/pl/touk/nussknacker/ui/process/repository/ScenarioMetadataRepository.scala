package pl.touk.nussknacker.ui.process.repository

import cats.data.OptionT
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.process.ScenarioMetadata
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

// TODO: we should replace FetchingProcessRepository and ProcessRepository by this class after we
//       split things like scenario graph (versions), scenario metadata, state related things (actions)
class ScenarioMetadataRepository(dbRef: DbRef)(implicit ec: ExecutionContext) extends NuTables {

  override protected val profile: JdbcProfile = dbRef.profile

  import profile.api._

  def getScenarioMetadata(scenarioName: ProcessName): DB[Option[ScenarioMetadata]] =
    (for {
      scenarioData <- OptionT(
        toEffectAll(
          processesTable
            .filter(_.name === scenarioName)
            .take(1)
            .result
            .headOption
        )
      )
      scenarioLabels <- OptionT.liftF(
        toEffectAll(
          labelsTable
            .filter(_.scenarioId === scenarioData.id)
            .map(_.label)
            .result
            .map(_.map(ScenarioLabel.apply).toList)
        )
      )
    } yield ScenarioMetadata(
      id = scenarioData.id,
      name = scenarioData.name,
      description = scenarioData.description,
      processCategory = scenarioData.processCategory,
      processingType = scenarioData.processingType,
      isFragment = scenarioData.isFragment,
      isArchived = scenarioData.isArchived,
      createdAt = scenarioData.createdAt,
      createdBy = scenarioData.createdBy,
      labels = scenarioLabels,
      impersonatedByIdentity = scenarioData.impersonatedByIdentity,
      impersonatedByUsername = scenarioData.impersonatedByUsername
    )).value

}
