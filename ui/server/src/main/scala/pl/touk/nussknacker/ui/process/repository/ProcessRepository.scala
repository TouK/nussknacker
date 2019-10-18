package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.{DeploymentEntry, ProcessHistoryEntry, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.EspTables
import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, LoggedUser, Permission}
import pl.touk.nussknacker.ui.util.DateUtils
import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError}

import scala.language.higherKinds

trait ProcessRepository[F[_]] extends Repository[F] with EspTables {

  import api._
  protected def processTableFilteredByUser(implicit loggedUser: LoggedUser): Query[ProcessEntityFactory#ProcessEntity, ProcessEntityData, Seq] = {
    loggedUser match {
      case user: CommonUser => processesTable.filter(_.processCategory inSet user.categories(Permission.Read))
      case _: AdminUser => processesTable
    }
  }

  protected def latestProcessVersions(processId: ProcessId)
                                     (implicit fetchShape: ProcessShapeFetchStrategy[_]): Query[ProcessVersionEntityFactory#BaseProcessVersionEntity, ProcessVersionEntityData, Seq] = {
    processVersionsTableQuery.filter(_.processId === processId.value).sortBy(_.createDate.desc)
  }

  protected def processVersionsTableQuery(implicit fetchShape: ProcessShapeFetchStrategy[_]): TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity] = {
    fetchShape match {
      case ProcessShapeFetchStrategy.Fetch => processVersionsTable.asInstanceOf[TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity]]
      case ProcessShapeFetchStrategy.NotFetch => processVersionsTableNoJson.asInstanceOf[TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity]]
    }
  }

  protected def latestProcessVersionsNoJson(processName: ProcessName): Query[ProcessVersionEntityFactory#BaseProcessVersionEntity, ProcessVersionEntityData, Seq] = {
    processesTable.filter(_.name === processName.value).
      join(processVersionsTableNoJson)
      .on { case (process, version) => process.id === version.id }
      .map(_._2)
      .sortBy(_.createDate.desc)
  }
}

object ProcessRepository {


  def toProcessHistoryEntry(process: ProcessEntityData,
            processVersion: ProcessVersionEntityData,
            deployedVersionsPerEnv: List[DeployedProcessVersionEntityData]): ProcessHistoryEntry = {
    ProcessHistoryEntry(
      processId = process.id.toString,
      processVersionId = processVersion.id,
      processName = process.name,
      createDate = DateUtils.toLocalDateTime(processVersion.createDate),
      user = processVersion.user,
      deployments = deployedVersionsPerEnv
        .collect { case deployedVersion if deployedVersion.processVersionId.contains(processVersion.id) =>
        toDeploymentEntry(deployedVersion)
      }
    )
  }

  def toDeploymentEntry(deployedVersion: DeployedProcessVersionEntityData): DeploymentEntry
    =  DeploymentEntry(deployedVersion.processVersionId.getOrElse(0),
                       deployedVersion.environment,
                       deployedVersion.deployedAtTime,
                       deployedVersion.user,
                       deployedVersion.buildInfo.flatMap(BuildInfo.parseJson).getOrElse(BuildInfo.empty))

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