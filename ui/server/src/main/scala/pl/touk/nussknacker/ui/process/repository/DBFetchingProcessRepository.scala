package pl.touk.nussknacker.ui.process.repository

import cats.data.OptionT
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.ui.db.EspTables.{deployedProcessesTable, processVersionsTable, tagsTable}
import pl.touk.nussknacker.ui.db.entity.ProcessDeploymentInfoEntity.{DeployedProcessVersionEntityData, DeploymentAction}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.{ProcessEntity, ProcessEntityData}
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.db.entity.TagsEntity.TagsEntityData
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{BaseProcessDetails, ProcessDetails, ProcessHistoryEntry}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.DateUtils
import pl.touk.nussknacker.ui.validation.ProcessValidation
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object DBFetchingProcessRepository {

  def create(dbConfig: DbConfig) =
    new DBFetchingProcessRepository[Future](dbConfig) with FetchingProcessRepository with BasicRepository

}

abstract class DBFetchingProcessRepository[F[_]](val dbConfig: DbConfig) extends ProcessRepository[F] with LazyLogging {

  import api._

  def fetchProcessesDetails()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ProcessDetails]] = {
    fetchProcessesDetailsByQuery(!_.isSubprocess)
  }

  def fetchSubProcessesDetails()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ProcessDetails]] = {
    fetchProcessesDetailsByQuery(_.isSubprocess)
  }

  private def fetchProcessesDetailsByQuery(query: ProcessEntity => Rep[Boolean])
                                          (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ProcessDetails]] = {
    val action = (for {
      tagsForProcesses <- tagsTable.result.map(_.toList.groupBy(_.processId).withDefaultValue(Nil))
      latestProcesses <- processVersionsTable.groupBy(_.processId).map { case (n, group) => (n, group.map(_.createDate).max) }
        .join(processVersionsTable).on { case (((processId, latestVersionDate)), processVersion) =>
        processVersion.processId === processId && processVersion.createDate === latestVersionDate
      }.join(processTableFilteredByUser.filter(query)).on { case ((_, latestVersion), process) => latestVersion.processId === process.id }
        .result
      deployedPerEnv <- latestDeployedProcessesVersionsPerEnvironment.result
    } yield latestProcesses.map { case ((_, processVersion), process) =>
      createFullDetails(process, processVersion, isLatestVersion = true,
        deployedPerEnv.map(_._1).filter(_._1 == process.id).map(_._2).toSet,
        tagsForProcesses(process.name), List.empty, businessView = false)
    }).map(_.toList)

    run(action)
  }

  def fetchLatestProcessDetailsForProcessId(id: String, businessView: Boolean = false)
                                           (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[ProcessDetails]] = {
    val action = (for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(id).result.headOption)
      processDetails <- fetchProcessDetailsForVersion(latestProcessVersion, isLatestVersion = true, businessView = businessView)
    } yield processDetails).value
    run(action)
  }

  def fetchProcessDetailsForId(processId: String, versionId: Long, businessView: Boolean)
                              (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[ProcessDetails]] = {
    val action = for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(processId).result.headOption)
      processVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(processId).filter(pv => pv.id === versionId).result.headOption)
      processDetails <- fetchProcessDetailsForVersion(processVersion, isLatestVersion = latestProcessVersion.id == processVersion.id, businessView = businessView)
    } yield processDetails
    run(action.value)
  }

  def fetchLatestProcessVersion(processId: String)
                               (implicit loggedUser: LoggedUser): F[Option[ProcessVersionEntityData]] = {
    val action = latestProcessVersions(processId).result.headOption
    run(action)
  }

  private def fetchProcessDetailsForVersion(processVersion: ProcessVersionEntityData, isLatestVersion: Boolean, businessView: Boolean = false)
                                           (implicit loggedUser: LoggedUser, ec: ExecutionContext) = {
    val id = processVersion.processId
    for {
      process <- OptionT[DB, ProcessEntityData](processTableFilteredByUser.filter(_.id === id).result.headOption)
      processVersions <- OptionT.liftF[DB, Seq[ProcessVersionEntityData]](latestProcessVersions(id).result)
      latestDeployedVersionsPerEnv <- OptionT.liftF[DB, Map[String, DeployedProcessVersionEntityData]](latestDeployedProcessVersionsPerEnvironment(id).result.map(_.toMap))
      tags <- OptionT.liftF[DB, Seq[TagsEntityData]](tagsTable.filter(_.processId === process.name).result)
    } yield createFullDetails(
      process = process,
      processVersion = processVersion,
      isLatestVersion = isLatestVersion,
      currentlyDeployedAt = latestDeployedVersionsPerEnv.keySet,
      tags = tags,
      history = processVersions.map(pvs => ProcessHistoryEntry(process, pvs, latestDeployedVersionsPerEnv)),
      businessView = businessView
    )
  }

  private def createFullDetails(process: ProcessEntityData,
                                processVersion: ProcessVersionEntityData,
                                isLatestVersion: Boolean,
                                currentlyDeployedAt: Set[String],
                                tags: Seq[TagsEntityData],
                                history: Seq[ProcessHistoryEntry],
                                businessView: Boolean)
                               (implicit loggedUser: LoggedUser): ProcessDetails = {
    BaseProcessDetails[DisplayableProcess](
      id = process.id,
      name = process.name,
      processVersionId = processVersion.id,
      isLatestVersion = isLatestVersion,
      description = process.description,
      processType = process.processType,
      processingType = process.processingType,
      processCategory = process.processCategory,
      currentlyDeployedAt = currentlyDeployedAt,
      tags = tags.map(_.name).toList,
      modificationDate = DateUtils.toLocalDateTime(processVersion.createDate),
      json = processVersion.json.map(jsonString => displayableFromJson(jsonString, process, businessView)),
      history = history.toList,
      modelVersion = processVersion.modelVersion
    )
  }

  private def displayableFromJson(json: String, process: ProcessEntityData, businessView: Boolean) = {
    ProcessConverter.toDisplayableOrDie(json, process.processingType, businessView = businessView)
  }

  private def latestDeployedProcessVersionsPerEnvironment(processId: String) = {
    latestDeployedProcessesVersionsPerEnvironment.filter(_._1._1 === processId).map { case ((_, env), deployedVersion) => (env, deployedVersion) }
  }

  private def latestDeployedProcessesVersionsPerEnvironment = {
    deployedProcessesTable.groupBy(e => (e.processId, e.environment)).map { case (processIdEnv, group) => (processIdEnv, group.map(_.deployedAt).max) }
      .join(deployedProcessesTable).on { case ((processIdEnv, maxDeployedAtForEnv), deplProc) =>
      deplProc.processId === processIdEnv._1 && deplProc.environment === processIdEnv._2 && deplProc.deployedAt === maxDeployedAtForEnv
    }.map { case ((env, _), deployedVersion) => env -> deployedVersion }.filter(_._2.deploymentAction === DeploymentAction.Deploy)
  }

}


