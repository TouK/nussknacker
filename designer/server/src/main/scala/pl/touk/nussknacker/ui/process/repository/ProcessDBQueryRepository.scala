package pl.touk.nussknacker.ui.process.repository

import java.sql.Timestamp
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.restmodel.processdetails.{ProcessShapeFetchStrategy, ProcessVersion}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.EspTables
import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, LoggedUser}
import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

//FIXME: It's temporary trait. In future we should merge and refactor: DBFetchingProcessRepository, ProcessDBQueryRepository and DBProcessRepository to one repository
trait ProcessDBQueryRepository[F[_]] extends Repository[F] with EspTables {
  import api._

  protected def processTableFilteredByUser(
      implicit loggedUser: LoggedUser
  ): Query[ProcessEntityFactory#ProcessEntity, ProcessEntityData, Seq] = {
    loggedUser match {
      case user: CommonUser => processesTable.filter(_.processCategory inSet user.categories(Permission.Read))
      case _: AdminUser     => processesTable
    }
  }

  protected def fetchProcessLatestVersionsQuery(processId: ProcessId)(
      implicit fetchShape: ProcessShapeFetchStrategy[_]
  ): Query[ProcessVersionEntityFactory#BaseProcessVersionEntity, ProcessVersionEntityData, Seq] =
    processVersionsTableQuery
      .filter(_.processId === processId)
      .sortBy(_.id.desc)

  protected def fetchLatestProcessesQuery(
      query: ProcessEntityFactory#ProcessEntity => Rep[Boolean],
      lastDeployedActionPerProcess: Set[ProcessId],
      isDeployed: Option[Boolean]
  )(implicit fetchShape: ProcessShapeFetchStrategy[_], loggedUser: LoggedUser): Query[
    (
        ((Rep[ProcessId], Rep[Option[Timestamp]]), ProcessVersionEntityFactory#BaseProcessVersionEntity),
        ProcessEntityFactory#ProcessEntity
    ),
    (((ProcessId, Option[Timestamp]), ProcessVersionEntityData), ProcessEntityData),
    Seq
  ] =
    processVersionsTableWithUnit
      .groupBy(_.processId)
      .map { case (n, group) => (n, group.map(_.createDate).max) }
      .join(processVersionsTableQuery)
      .on { case (((processId, latestVersionDate)), processVersion) =>
        processVersion.processId === processId && processVersion.createDate === latestVersionDate
      }
      .join(processTableFilteredByUser.filter(query))
      .on { case ((_, latestVersion), process) => latestVersion.processId === process.id }
      .filter { case ((_, _), process) =>
        isDeployed match {
          case None      => true: Rep[Boolean]
          case Some(dep) => process.id.inSet(lastDeployedActionPerProcess) === dep
        }
      }

  protected def processVersionsTableQuery(
      implicit fetchShape: ProcessShapeFetchStrategy[_]
  ): TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity] =
    fetchShape match {
      case ProcessShapeFetchStrategy.FetchDisplayable =>
        processVersionsTableWithScenarioJson
          .asInstanceOf[TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity]]
      case ProcessShapeFetchStrategy.FetchCanonical =>
        processVersionsTableWithScenarioJson
          .asInstanceOf[TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity]]
      case ProcessShapeFetchStrategy.NotFetch =>
        processVersionsTableWithUnit.asInstanceOf[TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity]]
      case ProcessShapeFetchStrategy.FetchComponentsUsages =>
        processVersionsTableWithComponentsUsages
          .asInstanceOf[TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity]]
    }

  protected def latestProcessVersionsNoJsonQuery(
      processName: ProcessName
  ): Query[ProcessVersionEntityFactory#BaseProcessVersionEntity, ProcessVersionEntityData, Seq] =
    processesTable
      .filter(_.name === processName)
      .join(processVersionsTableWithUnit)
      .on { case (process, version) => process.id === version.processId }
      .map(_._2)
      .sortBy(_.createDate.desc)
}

object ProcessDBQueryRepository {

  def toProcessVersion(versionData: ProcessVersionEntityData, actions: List[ProcessAction]): ProcessVersion =
    ProcessVersion(
      processVersionId = versionData.id,
      createDate = versionData.createDate.toInstant,
      modelVersion = versionData.modelVersion,
      user = versionData.user,
      actions = actions
    )

  final case class ProcessNotFoundError(id: String) extends Exception(s"No scenario $id found") with NotFoundError

  final case class ProcessAlreadyExists(id: String) extends BadRequestError {
    def getMessage = s"Scenario $id already exists"
  }

  final case class ProcessAlreadyDeployed(id: String) extends BadRequestError {
    def getMessage = s"Scenario $id is already deployed"
  }

  final case class InvalidProcessJson(rawJson: String) extends BadRequestError {
    def getMessage = s"Invalid raw json string: $rawJson"
  }
}
