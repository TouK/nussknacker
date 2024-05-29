package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.deployment.ProcessAction
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, ScenarioVersion, VersionId}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.db.NuTables
import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, LoggedUser}
import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError}

import java.sql.Timestamp
import scala.language.higherKinds

//FIXME: It's temporary trait. In future we should merge and refactor: DBFetchingProcessRepository, ProcessDBQueryRepository and DBProcessRepository to one repository
trait ProcessDBQueryRepository[F[_]] extends Repository[F] with NuTables {
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
      implicit fetchShape: ScenarioShapeFetchStrategy[_]
  ): Query[ProcessVersionEntityFactory#BaseProcessVersionEntity, ProcessVersionEntityData, Seq] =
    processVersionsTableQuery
      .filter(_.processId === processId)
      .sortBy(_.id.desc)

  protected def fetchLatestProcessesQuery(
      query: ProcessEntityFactory#ProcessEntity => Rep[Boolean],
      deployedProcesses: Set[ProcessId],
      isDeployed: Option[Boolean]
  )(implicit fetchShape: ScenarioShapeFetchStrategy[_], loggedUser: LoggedUser): Query[
    (ProcessVersionEntityFactory#ProcessVersionEntityWithUnit, ProcessEntityFactory#ProcessEntity),
    (ProcessVersionEntityData, ProcessEntityData),
    Seq
  ] =
    processVersionsTableWithUnit
      .join(processTableFilteredByUser.filter(query))
      .on { case (version, process) => version.processId === process.id && version.id === process.latestVersionId }
      .filter { case (_, process) =>
        isDeployed match {
          case None      => true: Rep[Boolean]
          case Some(dep) => process.id.inSet(deployedProcesses) === dep
        }
      }

  protected def processVersionsTableQuery(
      implicit fetchShape: ScenarioShapeFetchStrategy[_]
  ): TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity] =
    fetchShape match {
      case ScenarioShapeFetchStrategy.FetchScenarioGraph =>
        processVersionsTableWithScenarioJson
          .asInstanceOf[TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity]]
      case ScenarioShapeFetchStrategy.FetchCanonical =>
        processVersionsTableWithScenarioJson
          .asInstanceOf[TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity]]
      case ScenarioShapeFetchStrategy.NotFetch =>
        processVersionsTableWithUnit.asInstanceOf[TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity]]
      case ScenarioShapeFetchStrategy.FetchComponentsUsages =>
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

  def toProcessVersion(versionData: ProcessVersionEntityData, actions: List[ProcessAction]): ScenarioVersion =
    ScenarioVersion(
      processVersionId = versionData.id,
      createDate = versionData.createDate.toInstant,
      modelVersion = versionData.modelVersion,
      user = versionData.user,
      actions = actions
    )

  final case class ProcessNotFoundError(name: ProcessName) extends NotFoundError(s"No scenario $name found")

  final case class ProcessVersionNotFoundError(processName: ProcessName, version: VersionId)
      extends NotFoundError(s"Scenario $processName in version $version not found")

  final case class ProcessAlreadyExists(id: String) extends BadRequestError(s"Scenario $id already exists")

}
