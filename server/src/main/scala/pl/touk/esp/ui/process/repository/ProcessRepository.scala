package pl.touk.esp.ui.process.repository

import java.time.LocalDateTime

import cats.data._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.esp.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}
import pl.touk.esp.ui.EspError._
import pl.touk.esp.ui.db.entity.DeployedProcessVersionEntity.DeployedProcessVersionEntityData
import pl.touk.esp.ui.db.entity.ProcessEntity.{ProcessEntityData, ProcessType}
import pl.touk.esp.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.esp.ui.db.entity.TagsEntity.TagsEntityData
import pl.touk.esp.ui.db.EspTables._
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessType.ProcessType
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall.ProcessConverter
import pl.touk.esp.ui.process.repository.ProcessRepository.{InvalidProcessTypeError, ProcessDetails, ProcessHistoryEntry, ProcessNotFoundError}
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.esp.ui.util.DateUtils
import pl.touk.esp.ui.{BadRequestError, EspError, NotFoundError}
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.concurrent.ExecutionContext.Implicits.global

class ProcessRepository(db: JdbcBackend.Database,
                        driver: JdbcProfile,
                        processConverter: ProcessConverter) extends LazyLogging {
  import driver.api._

  def saveProcess(processId: String, processDeploymentData: ProcessDeploymentData)
                 (implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[XError[Unit]] = {
    logger.info(s"Saving process $processId by user $loggedUser")
    val (pType, maybeJson, maybeMainClass) = processDeploymentData match {
      case GraphProcess(json) => (ProcessType.Graph, Some(json), None)
      case CustomProcess(mainClass) => (ProcessType.Custom, None, Some(mainClass))
    }

    def processToInsert(process: Option[ProcessEntityData]): XError[Option[ProcessEntityData]] = process match {
      case Some(p) =>
        if (p.processType != pType) Xor.left(InvalidProcessTypeError(processId))
        else Xor.right(None)
      case None =>
        Xor.right(Option(ProcessEntityData(id = processId,
          name = processId, processCategory = ProcessRepository.defaultCategory,
          description = None, processType = pType)))
    }
    def versionToInsert(latestProcessVersion: Option[ProcessVersionEntityData],
                        processesVersionCount: Int): Option[ProcessVersionEntityData] = latestProcessVersion match {
      case Some(version) if version.json == maybeJson && version.mainClass == maybeMainClass => None
      case _ => Option(ProcessVersionEntityData(id = processesVersionCount + 1, processId = processId,
        json = maybeJson, mainClass = maybeMainClass, createDate = DateUtils.now, user = loggedUser.id))
    }
    val insertAction = for {
      process <- XorT.right[DB, EspError, Option[ProcessEntityData]](processTableFilteredByUser.filter(_.id === processId).result.headOption)
      processesVersionCount <- XorT.right[DB, EspError, Int](processVersionsTable.filter(p => p.processId === processId).length.result)
      latestProcessVersion <- XorT.right[DB, EspError, Option[ProcessVersionEntityData]](latestProcessVersions(processId).result.headOption)
      newProcess <- XorT.fromXor(processToInsert(process))
      newProcessVersion <- XorT.fromXor(Xor.right(versionToInsert(latestProcessVersion, processesVersionCount)))
      _ <- XorT.right[DB, EspError, Int](newProcess.map(processesTable += _).getOrElse(dbMonad.pure(0)))
      _ <- XorT.right[DB, EspError, Int](newProcessVersion.map(processVersionsTable += _).getOrElse(dbMonad.pure(0)))
    } yield ()
    db.run(insertAction.value)
  }

  //accessible only from initializing scripts so far
  def updateCategory(processId: String, category: String)(implicit loggedUser: LoggedUser) : Future[XError[Unit]] = {
    val processCat = for { c <- processesTable if c.id === processId } yield c.processCategory
    db.run(processCat.update(category)).map {
      case 0 => Xor.left(ProcessNotFoundError(processId))
      case 1 => Xor.right(())
    }
  }

  private def processTableFilteredByUser(implicit loggedUser: LoggedUser) =
    if (loggedUser.isAdmin) processesTable else processesTable.filter(_.processCategory inSet (loggedUser.categories :+ ProcessRepository.defaultCategory))

  def fetchProcessesDetails()
                           (implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[List[ProcessDetails]] = {
    val action = for {
      tagsForProcesses <- tagsTable.result.map(_.toList.groupBy(_.processId).withDefaultValue(Nil))
      latestProcesses <- processVersionsTable.groupBy(_.processId).map { case (n, group) => (n, group.map(_.createDate).max) }
        .join(processVersionsTable).on { case (((processId, latestVersionDate)), processVersion) =>
        processVersion.processId === processId && processVersion.createDate === latestVersionDate
      }.join(processTableFilteredByUser).on { case ((_, latestVersion), process) => latestVersion.processId === process.id }
        .result.map(_.map { case ((_, processVersion), process) => createFullDetails(process, processVersion, tagsForProcesses(process.name), List.empty) })
    } yield latestProcesses
    db.run(action).map(_.toList)
  }

  def fetchLatestProcessDetailsForProcessId(id: String)
                                           (implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Option[ProcessDetails]] = {
    val action = for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(id).result.headOption)
      processDetails <- fetchProcessDetailsForVersion(latestProcessVersion)
    } yield processDetails
    db.run(action.value)
  }

  def fetchProcessDetailsForId(processId: String, versionId: Long)
                              (implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Option[ProcessDetails]] = {
    val action = for {
      processVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(processId).filter(pv => pv.id === versionId).result.headOption)
      processDetails <- fetchProcessDetailsForVersion(processVersion)
    } yield processDetails
    db.run(action.value)
  }

  private def fetchProcessDetailsForVersion(processVersion: ProcessVersionEntityData)
                                           (implicit ec: ExecutionContext, loggedUser: LoggedUser)= {
    val id = processVersion.processId
    for {
      process <- OptionT[DB, ProcessEntityData](processTableFilteredByUser.filter(_.id === id).result.headOption)
      processVersions <- OptionT.liftF[DB, Seq[ProcessVersionEntityData]](latestProcessVersions(id).result)
      latestDeployedVersionsPerEnv <- OptionT.liftF[DB, Map[String, DeployedProcessVersionEntityData]](latestDeployedProcessVersionsPerEnvironment(id).result.map(_.toMap))
      tags <- OptionT.liftF[DB, Seq[TagsEntityData]](tagsTable.filter(_.processId === process.name).result)
    } yield createFullDetails(process, processVersion, tags, processVersions.map(pvs => ProcessHistoryEntry(process, pvs, latestDeployedVersionsPerEnv)))
  }

  private def createFullDetails(process: ProcessEntityData,
                                processVersion: ProcessVersionEntityData,
                                tags: Seq[TagsEntityData],
                                history: Seq[ProcessHistoryEntry]): ProcessDetails = {

    ProcessDetails(
      id = process.id,
      name = process.name,
      description = process.description,
      processType = process.processType,
      processCategory = process.processCategory,
      tags = tags.map(_.name).toList,
      modificationDate = DateUtils.toLocalDateTime(processVersion.createDate),
      json = processVersion.json.map(json => processConverter.toDisplayableOrDie(json)),
      history = history.toList
    )
  }

  def fetchLatestProcessVersion(processId: String)
                               (implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Option[ProcessVersionEntityData]] = {
    val action = latestProcessVersions(processId).result.headOption
    db.run(action)
  }

  private def latestProcessVersions(processId: String) = {
    processVersionsTable.filter(_.processId === processId).sortBy(_.createDate.desc)
  }

  private def latestDeployedProcessVersionsPerEnvironment(processId: String) = {
    deployedProcessesTable.filter(_.processId === processId).groupBy(_.environment).map { case (env, group) => (env, group.map(_.deployedAt).max) }
      .join(deployedProcessesTable).on { case ((env, maxDeployedAtForEnv), deplProc) =>
      deplProc.processId === processId && deplProc.environment === env && deplProc.deployedAt === maxDeployedAtForEnv
    }.map { case ((env, _), deployedVersion) => env -> deployedVersion }
  }
}

object ProcessRepository {

  val defaultCategory = "Default"

  case class ProcessDetails(
                             id: String,
                             name: String,
                             description: Option[String],
                             processType: ProcessType,
                             processCategory: String,
                             modificationDate: LocalDateTime,
                             tags: List[String],
                             json: Option[DisplayableProcess],
                             history: List[ProcessHistoryEntry]
                           )

  case class ProcessHistoryEntry(processId: String,
                                 processName: String,
                                 processVersionId: Long,
                                 createDate: LocalDateTime,
                                 user: String,
                                 deployments: List[DeploymentEntry]
                                )
  object ProcessHistoryEntry {
    def apply(process: ProcessEntityData,
              processVersion: ProcessVersionEntityData,
              deployedVersionsPerEnv: Map[String, DeployedProcessVersionEntityData]): ProcessHistoryEntry = {
      new ProcessHistoryEntry(
        processId = process.id,
        processVersionId = processVersion.id,
        processName = process.name,
        createDate = DateUtils.toLocalDateTime(processVersion.createDate),
        user = processVersion.user,
        deployments = deployedVersionsPerEnv.collect { case (env, deployedVersion) if deployedVersion.processVersionId == processVersion.id =>
          DeploymentEntry(env, deployedVersion.deployedAtTime)
        }.toList
      )
    }
  }

  case class DeploymentEntry(environment: String, deployedAt: LocalDateTime)

  case class ProcessNotFoundError(id: String) extends NotFoundError {
    def getMessage = s"No process $id found"
  }

  case class InvalidProcessTypeError(id: String) extends BadRequestError {
    def getMessage = s"Process $id is not GraphProcess"
  }

}