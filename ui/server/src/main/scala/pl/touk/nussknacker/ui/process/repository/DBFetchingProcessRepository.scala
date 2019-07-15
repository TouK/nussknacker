package pl.touk.nussknacker.ui.process.repository

import java.time.LocalDateTime

import cats.data.OptionT
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.DateUtils
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.graph.node.SubprocessInput
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.restmodel.ProcessType
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails._
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.process.deployment.ProcessIsBeingDeployed
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError

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

  def fetchProcesses(isSubprocess: Option[Boolean], isArchived: Option[Boolean], isDeployed: Option[Boolean], categories: Option[Seq[String]], processingTypes: Option[Seq[String]])
                    (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ProcessDetails]] = {

    val expr: List[Option[ProcessEntityFactory#ProcessEntity => Rep[Boolean]]] = List(
      Option(process => process.processType === ProcessType.Graph),
      isSubprocess.map(arg => process => process.isSubprocess === arg),
      isArchived.map(arg => process => process.isArchived === arg),
      categories.map(arg => process => process.processCategory.inSet(arg)),
      processingTypes.map(arg => process => process.processingType.inSet(arg))
    )

    run(fetchProcessDetailsByQueryAction ({ process =>
      expr.flatten.foldLeft(true: Rep[Boolean])((x, y) => x && y(process))
    }, isDeployed))
  }

  def fetchCustomProcesses()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ProcessDetails]]= {
    run(fetchProcessDetailsByQueryActionUnarchived(p => !p.isSubprocess && p.processType === ProcessType.Custom))
  }

  def fetchProcessesDetails()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ProcessDetails]] = {
    run(fetchProcessDetailsByQueryActionUnarchived(p => !p.isSubprocess))
  }

  def fetchProcessesDetails(processNames: List[ProcessName])(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ProcessDetails]] = {
    val processNamesSet = processNames.map(_.value).toSet
    run(fetchProcessDetailsByQueryActionUnarchived(p => !p.isSubprocess && p.name.inSet(processNamesSet)))
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
  private def fetchProcessDetailsByQueryActionUnarchived(query: ProcessEntityFactory#ProcessEntity => Rep[Boolean])
                                                (implicit loggedUser: LoggedUser, ec: ExecutionContext) =
    fetchProcessDetailsByQueryAction(e => query(e) && !e.isArchived)

  private def fetchProcessDetailsByQueryAction(query: ProcessEntityFactory#ProcessEntity => Rep[Boolean])
                                              (implicit loggedUser: LoggedUser, ec: ExecutionContext): api.DBIOAction[List[ProcessDetails], api.NoStream, Effect.All with Effect.Read] = {
    this.fetchProcessDetailsByQueryAction(query, None) //Back compatibility
  }

  private def fetchProcessDetailsByQueryAction(query: ProcessEntityFactory#ProcessEntity => Rep[Boolean], isDeployed: Option[Boolean])
                                              (implicit loggedUser: LoggedUser, ec: ExecutionContext): DBIOAction[List[ProcessDetails], NoStream, Effect.All with Effect.Read] = {

    (for {
      subprocessesVersions <- subprocessLastModificationDates

      tagsForProcesses <- tagsTable.result.map(_.toList.groupBy(_.processId).withDefaultValue(Nil))

      deployedPerEnv <- latestDeployedProcessesVersionsPerEnvironment.result

      //TODO: move it to SLICK DSL
      latestProcesses <- processVersionsTable.filter{
        raw => (isDeployed match {
          case None => true
          case Some(dep) => deployedPerEnv.map(_._1).contains(raw.id).equals(dep)
        }):Rep[Boolean]
      }
        .groupBy(_.processId)
        .map { case (n, group) => (n, group.map(_.createDate).max) }
        .join(processVersionsTable)
        .on { case (((processId, latestVersionDate)), processVersion) => processVersion.processId === processId && processVersion.createDate === latestVersionDate }
        .join(processTableFilteredByUser.filter(query))
        .on { case ((_, latestVersion), process) => latestVersion.processId === process.id }
        .result

    } yield
      latestProcesses.map { case ((_, processVersion), process) =>
        createFullDetails(
          process,
          processVersion,
          isLatestVersion = true,
          currentlyDeployedAt = deployedPerEnv.filter(_._1 == process.id).map(_._2),
          tags = tagsForProcesses(process.id),
          history = List.empty,
          businessView = false,
          subprocessesVersions = subprocessesVersions
        )
      }).map(_.toList)
  }

  def fetchLatestProcessDetailsForProcessId(id: ProcessId, businessView: Boolean = false)
                                           (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[ProcessDetails]] = {
    run(fetchLatestProcessDetailsForProcessIdQuery(id))
  }

  def fetchProcessDetailsForId(processId: ProcessId, versionId: Long, businessView: Boolean)
                              (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[ProcessDetails]] = {
    val action = for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(processId).result.headOption)
      processVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(processId).filter(pv => pv.id === versionId).result.headOption)
      processDetails <- fetchProcessDetailsForVersion(processVersion, isLatestVersion = latestProcessVersion.id == processVersion.id, businessView = businessView)
    } yield processDetails
    run(action.value)
  }

  def fetchLatestProcessVersion(processId: ProcessId)
                               (implicit loggedUser: LoggedUser): F[Option[ProcessVersionEntityData]] = {
    val action = latestProcessVersions(processId).result.headOption
    run(action)
  }

  def fetchProcessId(processName: ProcessName)(implicit ec: ExecutionContext): F[Option[ProcessId]] = {
    run(processesTable.filter(_.name === processName.value).map(_.id).result.headOption.map(_.map(ProcessId)))
  }

  def fetchProcessName(processId: ProcessId)(implicit ec: ExecutionContext): F[Option[ProcessName]] = {
    run(processesTable.filter(_.id === processId.value).map(_.name).result.headOption.map(_.map(ProcessName)))
  }

  def fetchDeploymentHistory(processId: ProcessId)(implicit ec: ExecutionContext): F[List[DeploymentHistoryEntry]] =
    run(deployedProcessesTable.filter(_.processId === processId.value)
      .sortBy(_.deployedAt.desc)
      .result.map(_.map(de =>
      DeploymentHistoryEntry(
        processVersionId = de.processVersionId.getOrElse(0),
        time = de.deployedAtTime,
        user = de.user,
        deploymentAction = de.deploymentAction,
        commentId = de.commentId,
        buildInfo = de.buildInfo.flatMap(BuildInfo.parseJson).getOrElse(BuildInfo.empty)
      )
    ).toList))

  def fetchProcessingType(processId: ProcessId)(implicit user: LoggedUser, ec: ExecutionContext): F[ProcessingType] = {
    run {
      fetchLatestProcessDetailsForProcessIdQuery(processId).flatMap {
        case None => DBIO.failed(ProcessNotFoundError(processId.value.toString))
        case Some(process) => DBIO.successful(process.processingType)
      }
    }
  }

  private def fetchLatestProcessDetailsForProcessIdQuery(id: ProcessId, businessView: Boolean = false)
                                                        (implicit loggedUser: LoggedUser, ec: ExecutionContext): DB[Option[ProcessDetails]] = {
    (for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(id).result.headOption)
      processDetails <-  fetchProcessDetailsForVersion(latestProcessVersion, isLatestVersion = true, businessView = businessView)
    } yield processDetails).value
  }

  private def fetchProcessDetailsForVersion(processVersion: ProcessVersionEntityData, isLatestVersion: Boolean, businessView: Boolean = false)
                                           (implicit loggedUser: LoggedUser, ec: ExecutionContext) = {
    val id = processVersion.processId
    for {
      subprocessesVersions <- OptionT.liftF[DB, Map[ProcessName, LocalDateTime]](subprocessLastModificationDates)
      process <- OptionT[DB, ProcessEntityData](processTableFilteredByUser.filter(_.id === id).result.headOption)
      processVersions <- OptionT.liftF[DB, Seq[ProcessVersionEntityData]](latestProcessVersions(ProcessId(id)).result)
      latestDeployedVersionsPerEnv <- OptionT.liftF[DB, Seq[DeployedProcessVersionEntityData]](latestDeployedProcessVersionsPerEnvironment(id).result)
      tags <- OptionT.liftF[DB, Seq[TagsEntityData]](tagsTable.filter(_.processId === process.id).result)
    } yield createFullDetails(
      process = process,
      processVersion = processVersion,
      isLatestVersion = isLatestVersion,
      currentlyDeployedAt = latestDeployedVersionsPerEnv,
      tags = tags,
      history = processVersions.map(pvs => ProcessRepository.toProcessHistoryEntry(process, pvs, latestDeployedVersionsPerEnv.toList)),
      businessView = businessView,
      subprocessesVersions = subprocessesVersions
    )
  }

  private def createFullDetails(process: ProcessEntityData,
                                processVersion: ProcessVersionEntityData,
                                isLatestVersion: Boolean,
                                currentlyDeployedAt: Seq[DeployedProcessVersionEntityData],
                                tags: Seq[TagsEntityData],
                                history: Seq[ProcessHistoryEntry],
                                businessView: Boolean,
                                subprocessesVersions: Map[ProcessName, LocalDateTime])
                               (implicit loggedUser: LoggedUser): ProcessDetails = {
    val displayable = processVersion.json.map(jsonString => displayableFromJson(jsonString, process, businessView))

    val subprocessModificationDate = displayable.map(findSubprocessesModificationDate(subprocessesVersions))

    BaseProcessDetails[DisplayableProcess](
      id = process.id.toString,
      name = process.name,
      processVersionId = processVersion.id,
      isLatestVersion = isLatestVersion,
      isArchived = process.isArchived,
      isSubprocess = process.isSubprocess,
      description = process.description,
      processType = process.processType,
      processingType = process.processingType,
      processCategory = process.processCategory,
      currentlyDeployedAt = currentlyDeployedAt.map(ProcessRepository.toDeploymentEntry).toList,
      currentDeployment = currentlyDeployedAt.headOption.map(ProcessRepository.toDeploymentEntry),
      tags = tags.map(_.name).toList,
      modificationDate = DateUtils.toLocalDateTime(processVersion.createDate),
      subprocessesModificationDate = subprocessModificationDate,
      json = displayable,
      history = history.toList,
      modelVersion = processVersion.modelVersion
    )
  }

  //TODO: is this the best way to find subprocesses modificationdate?
  private def subprocessLastModificationDates(implicit loggedUser: LoggedUser, ec: ExecutionContext) : DB[Map[ProcessName, LocalDateTime]] = {
    processesTable
      .filter(_.isSubprocess)
      .join(processVersionsTable)
      .on(_.id === _.processId).map { case (process, version) =>
        (process.name, version.createDate)
      }
      .groupBy(_._1)
      .map{ case (name, dates) => (name, dates.map(_._2).max) }
      .result.map { results =>
        results.flatMap { case (k, v) => v.map(ProcessName(k) -> _)}.toMap.mapValues(DateUtils.toLocalDateTime)
      }
  }

  private def findSubprocessesModificationDate(subprocessesVersions: Map[ProcessName, LocalDateTime])(process: DisplayableProcess)
    : Map[String, LocalDateTime] = {

    val allSubprocesses = process.nodes.collect {
      case SubprocessInput(_, SubprocessRef(subprocessId, _), _,_, _) => subprocessId
    }.toSet
    val floatingVersionSubprocesses = allSubprocesses -- process.metaData.subprocessVersions.keySet
    floatingVersionSubprocesses.flatMap(id => subprocessesVersions.get(ProcessName(id)).map((id, _))).toMap
  }


  private def displayableFromJson(json: String, process: ProcessEntityData, businessView: Boolean) = {
    ProcessConverter.toDisplayableOrDie(json, process.processingType, businessView = businessView)
  }

  private def latestDeployedProcessVersionsPerEnvironment(processId: Long) = {
    latestDeployedProcessesVersionsPerEnvironment.filter(_._1 === processId).map(_._2)
  }

  private def latestDeployedProcessesVersionsPerEnvironment = {
    deployedProcessesTable.groupBy(e => (e.processId, e.environment)).map { case (processIdEnv, group) => (processIdEnv, group.map(_.deployedAt).max) }
      .join(deployedProcessesTable).on { case ((processIdEnv, maxDeployedAtForEnv), deplProc) =>
      deplProc.processId === processIdEnv._1 && deplProc.environment === processIdEnv._2 && deplProc.deployedAt === maxDeployedAtForEnv
    }.map { case ((env, _), deployedVersion) => env._1 -> deployedVersion }.filter(_._2.deploymentAction === DeploymentAction.Deploy)
  }
}


