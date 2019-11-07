package pl.touk.nussknacker.ui.process.repository

import java.time.LocalDateTime

import cats.Monad
import cats.data.OptionT
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.{DB, _}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.ProcessType
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.{ProcessShapeFetchStrategy, _}
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.DateUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import cats.instances.future._

object DBFetchingProcessRepository {

  def create(dbConfig: DbConfig)(implicit ec: ExecutionContext) =
    new DBFetchingProcessRepository[Future](dbConfig) with BasicRepository

}

abstract class DBFetchingProcessRepository[F[_]: Monad](val dbConfig: DbConfig) extends FetchingProcessRepository[F] with LazyLogging {

  import api._

  override def fetchProcesses[PS: ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[BaseProcessDetails[PS]]] = {
    run(fetchProcessDetailsByQueryActionUnarchived(p => !p.isSubprocess && p.processType === ProcessType.Graph))
  }

  override def fetchProcesses[PS: ProcessShapeFetchStrategy](isSubprocess: Option[Boolean], isArchived: Option[Boolean],
                                                             isDeployed: Option[Boolean], categories: Option[Seq[String]], processingTypes: Option[Seq[String]])
                                                            (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[BaseProcessDetails[PS]]] = {

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

  override def fetchCustomProcesses[PS: ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[BaseProcessDetails[PS]]]= {
    run(fetchProcessDetailsByQueryActionUnarchived(p => !p.isSubprocess && p.processType === ProcessType.Custom))
  }

  override def fetchProcessesDetails[PS: ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[BaseProcessDetails[PS]]] = {
    run(fetchProcessDetailsByQueryActionUnarchived(p => !p.isSubprocess))
  }

  override def fetchProcessesDetails[PS: ProcessShapeFetchStrategy](processNames: List[ProcessName])
                                                                   (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[BaseProcessDetails[PS]]] = {
    val processNamesSet = processNames.map(_.value).toSet
    run(fetchProcessDetailsByQueryActionUnarchived(p => !p.isSubprocess && p.name.inSet(processNamesSet)))
  }

  override def fetchSubProcessesDetails[PS: ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[BaseProcessDetails[PS]]] = {
    run(fetchProcessDetailsByQueryActionUnarchived(p => p.isSubprocess))
  }

  override def fetchAllProcessesDetails[PS: ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[BaseProcessDetails[PS]]] = {
    run(fetchProcessDetailsByQueryActionUnarchived(_ => true))
  }

  override def fetchArchivedProcesses[PS: ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[BaseProcessDetails[PS]]] = {
    run(fetchProcessDetailsByQueryAction(_.isArchived))
  }

  private def fetchProcessDetailsByQueryActionUnarchived[PS: ProcessShapeFetchStrategy](query: ProcessEntityFactory#ProcessEntity => Rep[Boolean])
                                                                                       (implicit loggedUser: LoggedUser, ec: ExecutionContext) =
    fetchProcessDetailsByQueryAction(e => query(e) && !e.isArchived)

  private def fetchProcessDetailsByQueryAction[PS: ProcessShapeFetchStrategy](query: ProcessEntityFactory#ProcessEntity => Rep[Boolean])
                                                                             (implicit loggedUser: LoggedUser, ec: ExecutionContext): api.DBIOAction[List[BaseProcessDetails[PS]], api.NoStream, Effect.All with Effect.Read] = {
    this.fetchProcessDetailsByQueryAction(query, None) //Back compatibility
  }

  private def fetchProcessDetailsByQueryAction[PS: ProcessShapeFetchStrategy](query: ProcessEntityFactory#ProcessEntity => Rep[Boolean], isDeployed: Option[Boolean])
                                                                             (implicit loggedUser: LoggedUser, ec: ExecutionContext): DBIOAction[List[BaseProcessDetails[PS]], NoStream, Effect.All with Effect.Read] = {

    (for {
      subprocessesVersions <- subprocessLastModificationDates

      tagsForProcesses <- tagsTable.result.map(_.toList.groupBy(_.processId).withDefaultValue(Nil))

      deployedPerEnv <- latestDeployedProcessesVersionsPerEnvironment.result

      //TODO: move it to SLICK DSL
      latestProcesses <- processVersionsTableNoJson.filter{
        raw => (isDeployed match {
          case None => true:Rep[Boolean]
          case Some(dep) => raw.processId.inSet(deployedPerEnv.map(_._1)) === dep
        })
      }
        .groupBy(_.processId)
        .map { case (n, group) => (n, group.map(_.createDate).max) }
        .join(processVersionsTableQuery)
        .on { case (((processId, latestVersionDate)), processVersion) => processVersion.processId === processId && processVersion.createDate === latestVersionDate }
        .join(processTableFilteredByUser.filter(query))
        .on { case ((_, latestVersion), process) => latestVersion.processId === process.id }
        .result

    } yield
      latestProcesses.map { case ((_, processVersion), process) =>
        val processDetails = createFullDetails(
          process,
          processVersion,
          isLatestVersion = true,
          currentlyDeployedAt = deployedPerEnv.filter(_._1 == process.id).map(_._2),
          tags = tagsForProcesses(process.id),
          history = List.empty,
          businessView = false,
          subprocessesVersions = subprocessesVersions
        )
        processDetails
      }).map(_.toList)
  }

  override def fetchLatestProcessDetailsForProcessId[PS: ProcessShapeFetchStrategy](id: ProcessId, businessView: Boolean)
                                                                                   (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[BaseProcessDetails[PS]]] = {
    run(fetchLatestProcessDetailsForProcessIdQuery(id, businessView))
  }

  override def fetchProcessDetailsForId[PS: ProcessShapeFetchStrategy](processId: ProcessId, versionId: Long, businessView: Boolean)
                                                                      (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[BaseProcessDetails[PS]]] = {
    val action = for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(processId)(ProcessShapeFetchStrategy.NotFetch).result.headOption)
      processVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(processId).filter(pv => pv.id === versionId).result.headOption)
      processDetails <- fetchProcessDetailsForVersion(processVersion, isLatestVersion = latestProcessVersion.id == processVersion.id, businessView = businessView)
    } yield processDetails
    run(action.value)
  }

  override def fetchLatestProcessVersion[PS: ProcessShapeFetchStrategy](processId: ProcessId)
                                                                       (implicit loggedUser: LoggedUser): F[Option[ProcessVersionEntityData]] = {
    val action = latestProcessVersions(processId).result.headOption
    run(action)
  }

  override def fetchProcessId(processName: ProcessName)(implicit ec: ExecutionContext): F[Option[ProcessId]] = {
    run(processesTable.filter(_.name === processName.value).map(_.id).result.headOption.map(_.map(ProcessId)))
  }

  def fetchProcessName(processId: ProcessId)(implicit ec: ExecutionContext): F[Option[ProcessName]] = {
    run(processesTable.filter(_.id === processId.value).map(_.name).result.headOption.map(_.map(ProcessName(_))))
  }

  override def fetchDeploymentHistory(processId: ProcessId)(implicit ec: ExecutionContext): F[List[DeploymentHistoryEntry]] =
    run(deployedProcessesTable.filter(_.processId === processId.value)
      .joinLeft(commentsTable)
      .on { case (deployment, comment) => deployment.commentId === comment.id }
      .sortBy(_._1.deployedAt.desc)
      .result.map(_.map { case (de, comment) => DeploymentHistoryEntry(
        processVersionId = de.processVersionId.getOrElse(0),
        time = de.deployedAtTime,
        user = de.user,
        deploymentAction = de.deploymentAction,
        commentId = de.commentId,
        comment = comment.map(_.content),
        buildInfo = de.buildInfo.flatMap(BuildInfo.parseJson).getOrElse(BuildInfo.empty)
      )
    }.toList))

  override def fetchProcessingType(processId: ProcessId)(implicit user: LoggedUser, ec: ExecutionContext): F[ProcessingType] = {
    run {
      implicit val fetchStrategy: ProcessShapeFetchStrategy[_] = ProcessShapeFetchStrategy.NotFetch
      fetchLatestProcessDetailsForProcessIdQuery(processId, businessView = false).flatMap {
        case None => DBIO.failed(ProcessNotFoundError(processId.value.toString))
        case Some(process) => DBIO.successful(process.processingType)
      }
    }
  }

  private def fetchLatestProcessDetailsForProcessIdQuery[PS: ProcessShapeFetchStrategy](id: ProcessId, businessView: Boolean)
                                                                                       (implicit loggedUser: LoggedUser, ec: ExecutionContext): DB[Option[BaseProcessDetails[PS]]] = {
    (for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(id).result.headOption)
      processDetails <- fetchProcessDetailsForVersion(latestProcessVersion, isLatestVersion = true, businessView = businessView)
    } yield processDetails).value
  }

  private def fetchProcessDetailsForVersion[PS: ProcessShapeFetchStrategy](processVersion: ProcessVersionEntityData, isLatestVersion: Boolean, businessView: Boolean)
                                                                          (implicit loggedUser: LoggedUser, ec: ExecutionContext) = {
    val id = processVersion.processId
    for {
      subprocessesVersions <- OptionT.liftF[DB, Map[ProcessName, LocalDateTime]](subprocessLastModificationDates)
      process <- OptionT[DB, ProcessEntityData](processTableFilteredByUser.filter(_.id === id).result.headOption)
      processVersions <- OptionT.liftF[DB, Seq[ProcessVersionEntityData]](latestProcessVersions(ProcessId(id))(ProcessShapeFetchStrategy.NotFetch).result)
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

  private def createFullDetails[PS: ProcessShapeFetchStrategy](process: ProcessEntityData,
                                                               processVersion: ProcessVersionEntityData,
                                                               isLatestVersion: Boolean,
                                                               currentlyDeployedAt: Seq[DeployedProcessVersionEntityData],
                                                               tags: Seq[TagsEntityData],
                                                               history: Seq[ProcessHistoryEntry],
                                                               businessView: Boolean,
                                                               subprocessesVersions: Map[ProcessName, LocalDateTime])
                                                              (implicit loggedUser: LoggedUser): BaseProcessDetails[PS] = {
    BaseProcessDetails[PS](
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
      // asInstanceOf[PS] is ok here because there is only two options process is fetched (Option[ProcessDetails]) or not (None)
      json = processVersion.json.map(jsonString => displayableFromJson(jsonString, process, businessView).asInstanceOf[PS]),
      history = history.toList,
      modelVersion = processVersion.modelVersion
    )
  }

  //TODO: is this the best way to find subprocesses modificationdate?
  private def subprocessLastModificationDates(implicit loggedUser: LoggedUser, ec: ExecutionContext) : DB[Map[ProcessName, LocalDateTime]] = {
    processesTable
      .filter(_.isSubprocess)
      .join(processVersionsTableNoJson)
      .on(_.id === _.processId).map { case (process, version) =>
        (process.name, version.createDate)
      }
      .groupBy(_._1)
      .map{ case (name, dates) => (name, dates.map(_._2).max) }
      .result.map { results =>
        results.flatMap { case (k, v) => v.map(ProcessName(k) -> _)}.toMap.mapValues(DateUtils.toLocalDateTime)
      }
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


