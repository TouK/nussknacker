package pl.touk.nussknacker.ui.process.repository

import cats.data.NonEmptyList
import db.util.DBIOActionInstances.{DB, _}
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.db.entity.{ProcessEntityData, ProcessEntityFactory, ScenarioLabelEntityData}
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, ImpersonatedUser, LoggedUser, RealLoggedUser}
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

class ScenarioLabelsRepository(protected val dbRef: DbRef)(implicit ec: ExecutionContext) extends NuTables {

  override protected val profile: JdbcProfile = dbRef.profile

  private implicit val scenarioLabelOrdering: Ordering[ScenarioLabel] = Ordering.by(_.value)

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

  def getLabels(loggedUser: LoggedUser): DB[List[ScenarioLabel]] = {
    labelsByUser(loggedUser).result.map(_.map(ScenarioLabel.apply).toList)
  }

  def overwriteLabels(scenarioId: ProcessId, scenarioLabels: List[ScenarioLabel]): DB[Unit] =
    updateScenarioLabels(scenarioId, scenarioLabels)

  private def updateScenarioLabels(scenarioId: ProcessId, scenarioLabels: List[ScenarioLabel]): DBIO[Unit] = {
    val newLabels = scenarioLabels.toSet
    for {
      existingLabels <- findLabels(scenarioId)
      maybeLabelsToInsert = NonEmptyList.fromList((newLabels -- existingLabels).toList)
      maybeLabelsToRemove = NonEmptyList.fromList((existingLabels -- newLabels).toList)
      _: Unit <- maybeLabelsToInsert match {
        case Some(labelsToRemove) =>
          (labelsTable ++= labelsToRemove.toList.map(label => ScenarioLabelEntityData(label.value, scenarioId)))
            .map(_ => ())
        case None => dbMonad.unit
      }
      _: Unit <- maybeLabelsToRemove match {
        case Some(labelsToRemove) =>
          labelsTable
            .filter(_.scenarioId === scenarioId)
            .filter(_.label.inSet(labelsToRemove.toList.map(_.value).toSet))
            .delete
            .map(_ => ())
        case None => dbMonad.unit
      }
    } yield ()
  }

  private def findLabels(scenarioId: ProcessId) =
    findLabelsCompiled(scenarioId).result.map(_.map(toScenarioLabel).toSet)

  private def findLabelsCompiled =
    Compiled((scenarioId: Rep[ProcessId]) => labelsTable.filter(_.scenarioId === scenarioId))

  private def toScenarioLabel(entity: ScenarioLabelEntityData) = ScenarioLabel(entity.name)

  private def labelsByUser(loggedUser: LoggedUser) = {
    def getTableForUser(user: RealLoggedUser) = {
      user match {
        case user: CommonUser =>
          labelsTable
            .join(scenarioIdsFor(user))
            .on(_.scenarioId === _)
            .map(_._1.label)
            .distinct
        case _: AdminUser =>
          labelsTable.map(_.label).distinct
      }
    }
    loggedUser match {
      case user: RealLoggedUser   => getTableForUser(user)
      case user: ImpersonatedUser => getTableForUser(user.impersonatedUser)
    }
  }

  private def scenarioIdsFor(user: CommonUser) =
    processesTable.filter(_.processCategory inSet user.categories(Permission.Read)).map(_.id)

}
