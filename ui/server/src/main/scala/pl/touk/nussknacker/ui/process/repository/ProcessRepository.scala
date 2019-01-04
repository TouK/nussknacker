package pl.touk.nussknacker.ui.process.repository

import java.time.LocalDateTime

import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.EspTables._
import pl.touk.nussknacker.ui.db.entity.ProcessDeploymentInfoEntity.DeployedProcessVersionEntityData
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessEntityData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.db.entity.{ProcessEntity, ProcessVersionEntity}
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.{DeploymentEntry, ProcessHistoryEntry}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import pl.touk.nussknacker.ui.util.DateUtils
import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError}

import scala.language.higherKinds

trait ProcessRepository[F[_]] extends Repository[F] {

  import api._
  import pl.touk.nussknacker.ui.security.api.PermissionSyntax._
  protected def processTableFilteredByUser(implicit loggedUser: LoggedUser): Query[ProcessEntity.ProcessEntity, ProcessEntityData, Seq] = {
    val readCategories = loggedUser.can(Permission.Read)
    if (loggedUser.isAdmin) processesTable else processesTable.filter(_.processCategory inSet readCategories)
  }

  protected def latestProcessVersions(processId: ProcessId): Query[ProcessVersionEntity.ProcessVersionEntity, ProcessVersionEntityData, Seq] = {
    processVersionsTable.filter(_.processId === processId.value).sortBy(_.createDate.desc)
  }

  protected def latestProcessVersions(processName: ProcessName): Query[ProcessVersionEntity.ProcessVersionEntity, ProcessVersionEntityData, Seq] = {
    processesTable.filter(_.name === processName.value).
      join(processVersionsTable)
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
    =  DeploymentEntry(deployedVersion.processVersionId.getOrElse(0), deployedVersion.environment, deployedVersion.deployedAtTime, deployedVersion.user,
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