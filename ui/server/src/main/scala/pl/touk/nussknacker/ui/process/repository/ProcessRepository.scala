package pl.touk.nussknacker.ui.process.repository

import java.sql.Timestamp

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.{ProcessDeployment, ProcessHistoryEntry, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.EspTables
import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, LoggedUser, Permission}
import pl.touk.nussknacker.ui.util.DateUtils
import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

trait ProcessRepository[F[_]] extends Repository[F] with EspTables {
  import api._

  protected def processTableFilteredByUser(implicit loggedUser: LoggedUser): Query[ProcessEntityFactory#ProcessEntity, ProcessEntityData, Seq] = {
    loggedUser match {
      case user: CommonUser => processesTable.filter(_.processCategory inSet user.categories(Permission.Read))
      case _: AdminUser => processesTable
    }
  }

  protected def fetchProcessLatestVersions(processId: ProcessId)(implicit fetchShape: ProcessShapeFetchStrategy[_]): Query[ProcessVersionEntityFactory#BaseProcessVersionEntity, ProcessVersionEntityData, Seq] =
    processVersionsTableQuery
      .filter(_.processId === processId.value)
      .sortBy(_.createDate.desc)

  protected def fetchLastDeploymentActionPerProcess: Query[(Rep[Long], ProcessDeploymentInfoEntityFactory#ProcessDeploymentInfoEntity), (Long, DeployedProcessInfoEntityData), Seq] =
    deployedProcessesTable
      .groupBy(_.processId)
      .map { case (processId, group) => (processId, group.map(_.deployedAt).max) }
      .join(deployedProcessesTable)
      .on { case ((processId, latestDeployedAt), deployAction) => deployAction.processId === processId && deployAction.deployedAt === latestDeployedAt } //We fetch exactly this one  with max deployment
      .map { case ((processId, _), deployAction) => processId -> deployAction }

  protected def fetchProcessLatestDeployActions(processId: Long): Query[ProcessDeploymentInfoEntityFactory#ProcessDeploymentInfoEntity, DeployedProcessInfoEntityData, Seq] =
    deployedProcessesTable
      .filter(_.processId === processId)
      .sortBy(_.deployedAt.desc)

  protected def fetchLatestProcesses(query: ProcessEntityFactory#ProcessEntity => Rep[Boolean],
                                     deploymentsPerEnv: Seq[(Long, DeployedProcessInfoEntityData)],
                                     isDeployed: Option[Boolean])(implicit fetchShape: ProcessShapeFetchStrategy[_], loggedUser: LoggedUser, ec: ExecutionContext): Query[(((Rep[Long], Rep[Option[Timestamp]]), ProcessVersionEntityFactory#BaseProcessVersionEntity), ProcessEntityFactory#ProcessEntity), (((Long, Option[Timestamp]), ProcessVersionEntityData), ProcessEntityData), Seq] =
    processVersionsTableNoJson
      .groupBy(_.processId)
      .map { case (n, group) => (n, group.map(_.createDate).max) }
      .join(processVersionsTableQuery)
      .on { case (((processId, latestVersionDate)), processVersion) => processVersion.processId === processId && processVersion.createDate === latestVersionDate }
      .join(processTableFilteredByUser.filter(query))
      .on { case ((_, latestVersion), process) => latestVersion.processId === process.id }
      .filter{ case ((_, _), process) =>
        isDeployed match {
          case None => true: Rep[Boolean]
          case Some(dep) => process.id.inSet(deploymentsPerEnv.filter(_._2.isDeployed).map(_._1)) === dep
        }
      }

  protected def fetchTagsPerProcess(implicit fetchShape: ProcessShapeFetchStrategy[_], ec: ExecutionContext): DBIOAction[Map[Long, List[TagsEntityData]], NoStream, Effect.Read] =
    tagsTable.result.map(_.toList.groupBy(_.processId).withDefaultValue(Nil))

  protected def processVersionsTableQuery(implicit fetchShape: ProcessShapeFetchStrategy[_]): TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity] =
    fetchShape match {
      case ProcessShapeFetchStrategy.FetchDisplayable | ProcessShapeFetchStrategy.FetchCanonical =>
        processVersionsTable.asInstanceOf[TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity]]
      case ProcessShapeFetchStrategy.NotFetch =>
        processVersionsTableNoJson.asInstanceOf[TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity]]
    }

  protected def latestProcessVersionsNoJson(processName: ProcessName): Query[ProcessVersionEntityFactory#BaseProcessVersionEntity, ProcessVersionEntityData, Seq] =
    processesTable
      .filter(_.name === processName.value)
      .join(processVersionsTableNoJson)
      .on { case (process, version) => process.id === version.id }
      .map(_._2)
      .sortBy(_.createDate.desc)
}

object ProcessRepository {

  def toProcessHistoryEntry(process: ProcessEntityData, processVersion: ProcessVersionEntityData, allDeployments: List[DeployedProcessInfoEntityData]): ProcessHistoryEntry = ProcessHistoryEntry(
    processId = process.id.toString,
    processVersionId = processVersion.id,
    processName = process.name,
    createDate = DateUtils.toLocalDateTime(processVersion.createDate),
    user = processVersion.user,
    deployments = allDeployments.collect {
      case deployedVersion if deployedVersion.processVersionId.equals(processVersion.id) => toDeploymentEntry(deployedVersion)
    }
  )

  def toDeploymentEntry(deployedProcessInfoEntityData: DeployedProcessInfoEntityData): ProcessDeployment = ProcessDeployment(
    processVersionId = deployedProcessInfoEntityData.processVersionId,
    environment = deployedProcessInfoEntityData.environment,
    deployedAt = deployedProcessInfoEntityData.deployedAtTime,
    user = deployedProcessInfoEntityData.user,
    action = deployedProcessInfoEntityData.deploymentAction,
    buildInfo = deployedProcessInfoEntityData.buildInfo.flatMap(BuildInfo.parseJson).getOrElse(BuildInfo.empty)
  )

  case class ProcessNotFoundError(id: String) extends Exception(s"No process $id found") with NotFoundError

  case class ProcessAlreadyExists(id: String) extends BadRequestError {
    def getMessage = s"Process $id already exists"
  }

  case class ProcessAlreadyDeployed(id: String) extends BadRequestError {
    def getMessage = s"Process $id is already deployed"
  }

  case class InvalidProcessTypeError(id: String) extends BadRequestError {
    def getMessage = s"Process $id is not GraphProcess"
  }
}