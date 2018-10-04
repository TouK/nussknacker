package pl.touk.nussknacker.ui.process.repository

import java.sql.{Date, Timestamp}
import java.time.LocalDateTime

import cats.data.OptionT
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.ui.db.EspTables.{deployedProcessesTable, processVersionsTable, processesTable, tagsTable}
import pl.touk.nussknacker.ui.db.entity.ProcessDeploymentInfoEntity.{DeployedProcessVersionEntityData, DeploymentAction}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.{ProcessEntity, ProcessEntityData, ProcessType}
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.db.entity.TagsEntity.TagsEntityData
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{BaseProcessDetails, BasicProcess, ProcessDetails, ProcessHistoryEntry}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.DateUtils
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.graph.node.{SubprocessInput, SubprocessNode}
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
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

  def fetchProcesses()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ProcessDetails]] = {
    run(fetchProcessDetailsByQueryActionUnarchived(p => !p.isSubprocess && p.processType === ProcessType.Graph))
  }

  def fetchCustomProcesses()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ProcessDetails]]= {
    run(fetchProcessDetailsByQueryActionUnarchived(p => !p.isSubprocess && p.processType === ProcessType.Custom))
  }

  def fetchProcessesDetails()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ProcessDetails]] = {
    run(fetchProcessDetailsByQueryActionUnarchived(p => !p.isSubprocess))
  }

  def fetchSubProcessesDetails()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ProcessDetails]] = {
    run(fetchProcessDetailsByQueryActionUnarchived(p => p.isSubprocess))
  }

  def fetchAllProcessesDetails()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ProcessDetails]] = {
    run(fetchProcessDetailsByQueryActionUnarchived(_ => true))
  }

  def fetchArchivedProcesses()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ProcessDetails]] = {
    run(fetchProcessDetailsByQueryAction(_.isArchived))
  }
  private def fetchProcessDetailsByQueryActionUnarchived(query: ProcessEntity => Rep[Boolean])
                                                (implicit loggedUser: LoggedUser, ec: ExecutionContext) =
    fetchProcessDetailsByQueryAction(e => query(e) && !e.isArchived)
  
  private def fetchProcessDetailsByQueryAction(query: ProcessEntity => Rep[Boolean])
                                              (implicit loggedUser: LoggedUser, ec: ExecutionContext) = {
    (for {
      subprocessesVersions <- subprocessLastModificationDates

      tagsForProcesses <- tagsTable.result.map(_.toList.groupBy(_.processId).withDefaultValue(Nil))

      latestProcesses <- processVersionsTable
        .groupBy(_.processId)
        .map { case (n, group) => (n, group.map(_.createDate).max) }
        .join(processVersionsTable)
        .on { case (((processId, latestVersionDate)), processVersion) => processVersion.processId === processId && processVersion.createDate === latestVersionDate }
        .join(processTableFilteredByUser.filter(query))
        .on { case ((_, latestVersion), process) => latestVersion.processId === process.id }
        .result

      deployedPerEnv <- latestDeployedProcessesVersionsPerEnvironment.result

    } yield
      latestProcesses.map { case ((_, processVersion), process) =>
        createFullDetails(
          process,
          processVersion,
          isLatestVersion = true,
          currentlyDeployedAt = deployedPerEnv.map(_._1).filter(_._1 == process.id).map(_._2).toSet,
          tags = tagsForProcesses(process.id),
          history = List.empty,
          businessView = false,
          subprocessesVersions = subprocessesVersions
        )
      }).map(_.toList)
  }

  def fetchLatestProcessDetailsForProcessId(id: String, businessView: Boolean = false)
                                           (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[ProcessDetails]] = {
    val action = (for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(id).result.headOption)
      processDetails <-  fetchProcessDetailsForVersion(latestProcessVersion, isLatestVersion = true, businessView = businessView)
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

  def fetchProcessId(processName: String): F[Option[String]] = {
    run(processesTable.filter(_.name === processName).map(_.id).result.headOption)
  }

  def fetchProcessName(processId: String): F[Option[String]] = {
    run(processesTable.filter(_.id === processId).map(_.name).result.headOption)
  }

  private def fetchProcessDetailsForVersion(processVersion: ProcessVersionEntityData, isLatestVersion: Boolean, businessView: Boolean = false)
                                           (implicit loggedUser: LoggedUser, ec: ExecutionContext) = {
    val id = processVersion.processId
    for {
      subprocessesVersions <- OptionT.liftF[DB, Map[String, LocalDateTime]](subprocessLastModificationDates)
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
      businessView = businessView,
      subprocessesVersions = subprocessesVersions
    )
  }

  private def createFullDetails(process: ProcessEntityData,
                                processVersion: ProcessVersionEntityData,
                                isLatestVersion: Boolean,
                                currentlyDeployedAt: Set[String],
                                tags: Seq[TagsEntityData],
                                history: Seq[ProcessHistoryEntry],
                                businessView: Boolean,
                                subprocessesVersions: Map[String, LocalDateTime])
                               (implicit loggedUser: LoggedUser): ProcessDetails = {
    val displayable = processVersion.json.map(jsonString => displayableFromJson(jsonString, process, businessView))

    val subprocessModificationDate = displayable.map(findSubprocessesModificationDate(subprocessesVersions))

    BaseProcessDetails[DisplayableProcess](
      id = process.id,
      name = process.name,
      processVersionId = processVersion.id,
      isLatestVersion = isLatestVersion,
      isArchived = process.isArchived,
      description = process.description,
      processType = process.processType,
      processingType = process.processingType,
      processCategory = process.processCategory,
      currentlyDeployedAt = currentlyDeployedAt,
      tags = tags.map(_.name).toList,
      modificationDate = DateUtils.toLocalDateTime(processVersion.createDate),
      subprocessesModificationDate = subprocessModificationDate,
      json = displayable,
      history = history.toList,
      modelVersion = processVersion.modelVersion
    )
  }

  //TODO: is this the best way to find subprocesses modificationdate?
  private def subprocessLastModificationDates(implicit loggedUser: LoggedUser, ec: ExecutionContext) : DB[Map[String, LocalDateTime]] = {
    processesTable
      .filter(_.isSubprocess)
      .join(processVersionsTable)
      .on(_.id === _.processId).map { case (_, version) =>
      (version.processId, version.createDate)
    }.groupBy(_._1)
     .map{ case (id, dates) => (id, dates.map(_._2).max) }
     .result.map { results =>
      results.flatMap { case (k, v) => v.map(k -> _)}.toMap.mapValues(DateUtils.toLocalDateTime)
    }
  }

  private def findSubprocessesModificationDate(subprocessesVersions: Map[String, LocalDateTime])(process: DisplayableProcess)
    : Map[String, LocalDateTime] = {

    val allSubprocesses = process.nodes.collect {
      case SubprocessInput(_, SubprocessRef(subprocessId, _), _,_) => subprocessId
    }.toSet
    val floatingVersionSubprocesses = allSubprocesses -- process.metaData.subprocessVersions.keySet
    floatingVersionSubprocesses.flatMap(id => subprocessesVersions.get(id).map((id, _))).toMap
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


