package pl.touk.nussknacker.ui.process.repository

import cats.data.NonEmptyList
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.ui.db.entity.ScenarioLabelEntityData
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import slick.jdbc.JdbcProfile
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.ui.process.repository.ScenarioLabelsRepository.ScenarioLabel

import scala.concurrent.ExecutionContext

object ScenarioLabelsRepository {
  final case class ScenarioLabel(value: String)

  object ScenarioLabel {
    implicit val scenarioLabelOrdering: Ordering[ScenarioLabel] = Ordering.by(_.value)
  }

}

class ScenarioLabelsRepository(protected val dbRef: DbRef)(implicit ec: ExecutionContext) extends NuTables {

  override protected val profile: JdbcProfile = dbRef.profile

  import profile.api._

  def getLabels(processId: ProcessId): DB[List[ScenarioLabel]] = findLabels(processId).map(_.toList.sorted)

  def getLabels: DB[Map[ProcessId, List[ScenarioLabel]]] = {
    labelsTable.result
      .map {
        _.groupBy(_.scenarioId).map { case (scenarioId, tagsEntities) =>
          (scenarioId, tagsEntities.map(toScenarioLabel).toList)
        }
      }
  }

  def overwriteLabels(scenarioId: ProcessId, scenarioLabels: List[ScenarioLabel]): DB[Unit] =
    updateScenarioLabels(scenarioId, scenarioLabels)

  private def updateScenarioLabels(scenarioId: ProcessId, scenarioLabels: List[ScenarioLabel]): DBIO[Unit] = {
    val newLabels = scenarioLabels.toSet
    for {
      existingLabels <- findLabels(scenarioId)
      maybeLabelsToInsert = NonEmptyList.fromList((newLabels -- existingLabels).toList)
      maybeLabelsToRemove = NonEmptyList.fromList((existingLabels -- newLabels).toList)
      _ <- maybeLabelsToInsert match {
        case Some(labelsToRemove) =>
          labelsTable ++= labelsToRemove.toList.map(label => ScenarioLabelEntityData(label.value, scenarioId))
        case None => dbMonad.pure(None)
      }
      _ <- maybeLabelsToRemove match {
        case Some(labelsToRemove) =>
          labelsTable
            .filter(_.scenarioId === scenarioId)
            .filter(_.name.inSet(labelsToRemove.toList.map(_.value).toSet))
            .delete
        case None => dbMonad.pure(0)
      }
    } yield ()
  }

  private def findLabels(scenarioId: ProcessId) =
    findLabelsCompiled(scenarioId).result.map(_.map(toScenarioLabel).toSet)

  private def findLabelsCompiled =
    Compiled((scenarioId: Rep[ProcessId]) => labelsTable.filter(_.scenarioId === scenarioId))

  private def toScenarioLabel(entity: ScenarioLabelEntityData) = ScenarioLabel(entity.name)

}
