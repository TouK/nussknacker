package pl.touk.esp.ui.process.repository

import java.time.LocalDateTime

import cats.data._
import db.util.DBIOActionInstances._
import pl.touk.esp.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}
import pl.touk.esp.ui.EspError._
import pl.touk.esp.ui.db.migration.CreateDeployedProcessesMigration.DeployedProcessVersionEntityData
import pl.touk.esp.ui.db.migration.CreateProcessesMigration.ProcessType.ProcessType
import pl.touk.esp.ui.db.migration.CreateProcessesMigration.{ProcessEntityData, ProcessType, ProcessVersionEntityData}
import pl.touk.esp.ui.db.migration.CreateTagsMigration.TagsEntityData
import pl.touk.esp.ui.db.migration.{CreateDeployedProcessesMigration, CreateProcessVersionsMigration, CreateProcessesMigration, CreateTagsMigration}
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall.ProcessConverter
import pl.touk.esp.ui.process.repository.ProcessRepository.{InvalidProcessTypeError, ProcessDetails, ProcessHistoryEntry}
import pl.touk.esp.ui.util.DateUtils
import pl.touk.esp.ui.{BadRequestError, EspError, NotFoundError}
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

class ProcessRepository(db: JdbcBackend.Database,
                        driver: JdbcProfile,
                        processConverter: ProcessConverter) {

  private val processesMigration = new CreateProcessesMigration {
    override protected val profile: JdbcProfile = ProcessRepository.this.driver
  }

  private val processVersionsMigration = new CreateProcessVersionsMigration {
    override protected val profile: JdbcProfile = ProcessRepository.this.driver
  }

  private val deployedProcessesMigration = new CreateDeployedProcessesMigration {
    override protected val profile: JdbcProfile = ProcessRepository.this.driver
  }

  private val tagsMigration = new CreateTagsMigration {
    override protected val profile: JdbcProfile = ProcessRepository.this.driver
  }

  import deployedProcessesMigration._
  import driver.api._
  import processesMigration._
  import processVersionsMigration._
  import tagsMigration._

  def saveProcess(processId: String, processDeploymentData: ProcessDeploymentData, user: String)(implicit ec: ExecutionContext): Future[XError[Unit]] = {
    val (pType, maybeJson, maybeMainClass) = processDeploymentData match {
      case GraphProcess(json) => (ProcessType.Graph, Some(json), None)
      case CustomProcess(mainClass) => (ProcessType.Custom, None, Some(mainClass))
    }
    def processToInsert(process: Option[ProcessEntityData]): XError[Option[ProcessEntityData]] = process match {
      case Some(p) =>
        if (p.processType != pType) Xor.left(InvalidProcessTypeError(processId))
        else Xor.right(None)
      case None =>
        Xor.right(Option(ProcessEntityData(id = processId, name = processId, description = None, processType = pType)))
    }
    def versionToInsert(latestProcessVersion: Option[ProcessVersionEntityData],
                        processesVersionCount: Int): Option[ProcessVersionEntityData] = latestProcessVersion match {
      case Some(version) if version.json == maybeJson && version.mainClass == maybeMainClass => None
      case _ => Option(ProcessVersionEntityData(id = processesVersionCount + 1, processId = processId,
        json = maybeJson, mainClass = maybeMainClass, createDate = DateUtils.now, user = user))
    }
    val insertAction = for {
      process <- XorT.right[DB, EspError, Option[ProcessEntityData]](processesTable.filter(_.id === processId).result.headOption)
      processesVersionCount <- XorT.right[DB, EspError, Int](processVersionsTable.filter(p => p.processId === processId).length.result)
      latestProcessVersion <- XorT.right[DB, EspError, Option[ProcessVersionEntityData]](latestProcessVersions(processId).result.headOption)
      newProcess <- XorT.fromXor(processToInsert(process))
      newProcessVersion <- XorT.fromXor(Xor.right(versionToInsert(latestProcessVersion, processesVersionCount)))
      _ <- XorT.right[DB, EspError, Int](newProcess.map(processesTable += _).getOrElse(dbMonad.pure(0)))
      _ <- XorT.right[DB, EspError, Int](newProcessVersion.map(processVersionsTable += _).getOrElse(dbMonad.pure(0)))
    } yield ()
    db.run(insertAction.value)
  }

  def fetchProcessesDetails()
                           (implicit ec: ExecutionContext): Future[List[ProcessDetails]] = {
    val action = for {
      tagsForProcesses <- tagsTable.result.map(_.toList.groupBy(_.processId).withDefaultValue(Nil))
      latestProcesses <- processVersionsTable.groupBy(_.processId).map { case (n, group) => (n, group.map(_.createDate).max) }
        .join(processVersionsTable).on { case (((processId, latestVersionDate)), processVersion) =>
        processVersion.processId === processId && processVersion.createDate === latestVersionDate
      }.join(processesTable).on { case ((_, latestVersion), process) => latestVersion.processId === process.id }
        .result.map(_.map { case ((_, processVersion), process) => createFullDetails(process, processVersion, tagsForProcesses(process.name), List.empty) })
    } yield latestProcesses
    db.run(action).map(_.toList)
  }

  def fetchLatestProcessDetailsForProcessId(id: String)
                                           (implicit ec: ExecutionContext): Future[Option[ProcessDetails]] = {
    val action = for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(id).result.headOption)
      processDetails <- fetchProcessDetailsForVersion(latestProcessVersion)
    } yield processDetails
    db.run(action.value)
  }

  def fetchProcessDetailsForId(processId: String, versionId: Long)
                              (implicit ec: ExecutionContext): Future[Option[ProcessDetails]] = {
    val action = for {
      processVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(processId).filter(pv => pv.id === versionId).result.headOption)
      processDetails <- fetchProcessDetailsForVersion(processVersion)
    } yield processDetails
    db.run(action.value)
  }

  private def fetchProcessDetailsForVersion(processVersion: ProcessVersionEntityData)
                                           (implicit ec: ExecutionContext)= {
    val id = processVersion.processId
    for {
      process <- OptionT[DB, ProcessEntityData](processesTable.filter(_.id === id).result.headOption)
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
      tags = tags.map(_.name).toList,
      json = processVersion.json.map(json => processConverter.toDisplayableOrDie(json)),
      history = history.toList
    )
  }

  def fetchLatestProcessVersion(processId: String)
                               (implicit ec: ExecutionContext): Future[Option[ProcessVersionEntityData]] = {
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

  case class ProcessDetails(
                             id: String,
                             name: String,
                             description: Option[String],
                             processType: ProcessType,
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