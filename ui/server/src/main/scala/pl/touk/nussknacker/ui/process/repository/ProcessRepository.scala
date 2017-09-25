package pl.touk.nussknacker.ui.process.repository

import java.time.LocalDateTime

import cats.data._
import cats.syntax.either._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.EspError._
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.EspTables._
import pl.touk.nussknacker.ui.db.entity.ProcessDeploymentInfoEntity.{DeployedProcessVersionEntityData, DeploymentAction}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessType.ProcessType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.{ProcessEntity, ProcessEntityData, ProcessType}
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.db.entity.TagsEntity.TagsEntityData
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessRepository._
import pl.touk.nussknacker.ui.security.LoggedUser
import pl.touk.nussknacker.ui.util.DateUtils
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.nussknacker.ui.{BadRequestError, EspError, NotFoundError}
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

//TODO: clean up methods - especially finders...
class ProcessRepository(db: JdbcBackend.Database,
                        driver: JdbcProfile,
                        processValidation: ProcessValidation, modelVersion: Map[ProcessingType, Int])(implicit ec: ExecutionContext) extends LazyLogging {
  import driver.api._

  def saveNewProcess(processId: String, category: String, processDeploymentData: ProcessDeploymentData,
                     processingType: ProcessingType, isSubprocess: Boolean)
                    (implicit loggedUser: LoggedUser): Future[XError[Unit]] = {
    logger.info(s"Saving process $processId by user $loggedUser")

    val processToSave = ProcessEntityData(id = processId, name = processId, processCategory = category,
              description = None, processType = processType(processDeploymentData),
                processingType = processingType, isSubprocess = isSubprocess)

    val insertAction =
      (processesTable += processToSave).andThen(updateProcessInternal(processId, processDeploymentData))
    db.run(insertAction.transactionally).map(_.map(_ => ()))
  }

  def updateProcess(processId: String, processDeploymentData: ProcessDeploymentData)
                 (implicit loggedUser: LoggedUser): Future[XError[Option[ProcessVersionEntityData]]] = {
    val update = updateProcessInternal(processId, processDeploymentData)
    db.run(update.transactionally)
  }

  def deleteProcess(processId: String) : Future[XError[Unit]] = {
    db.run(processesTable.filter(_.id === processId).delete.transactionally).map {
      case 0 => Left(ProcessNotFoundError(processId))
      case 1 => Right(())
    }
  }

  private def updateProcessInternal(processId: String, processDeploymentData: ProcessDeploymentData)
                   (implicit loggedUser: LoggedUser): DB[XError[Option[ProcessVersionEntityData]]] = {
    logger.info(s"Updating process $processId by user $loggedUser")
    val (maybeJson, maybeMainClass) = processDeploymentData match {
      case GraphProcess(json) => (Some(json), None)
      case CustomProcess(mainClass) => (None, Some(mainClass))
    }

    def versionToInsert(latestProcessVersion: Option[ProcessVersionEntityData],
                        processesVersionCount: Int, processingType: ProcessingType): Option[ProcessVersionEntityData] = latestProcessVersion match {
      case Some(version) if version.json == maybeJson && version.mainClass == maybeMainClass => None
      case _ => Option(ProcessVersionEntityData(id = processesVersionCount + 1, processId = processId,
        json = maybeJson, mainClass = maybeMainClass, createDate = DateUtils.now,
        user = loggedUser.id, modelVersion = modelVersion.get(processingType)))
    }
    val insertAction = for {
      maybeProcess <- EitherT.right[DB, EspError, Option[ProcessEntityData]](processTableFilteredByUser.filter(_.id === processId).result.headOption)
      process <- EitherT.fromEither(Either.fromOption(maybeProcess, ProcessNotFoundError(processId)))
      _ <- EitherT.fromEither(Either.cond(process.processType == processType(processDeploymentData), (), InvalidProcessTypeError(processId)))
      processesVersionCount <- EitherT.right[DB, EspError, Int](processVersionsTable.filter(p => p.processId === processId).length.result)
      latestProcessVersion <- EitherT.right[DB, EspError, Option[ProcessVersionEntityData]](latestProcessVersions(processId).result.headOption)
      newProcessVersion <- EitherT.fromEither(Right(versionToInsert(latestProcessVersion, processesVersionCount, process.processingType)))
      _ <- EitherT.right[DB, EspError, Int](newProcessVersion.map(processVersionsTable += _).getOrElse(dbMonad.pure(0)))
    } yield newProcessVersion
    insertAction.value
  }

  //accessible only from initializing scripts so far
  def updateCategory(processId: String, category: String)(implicit loggedUser: LoggedUser) : Future[XError[Unit]] = {
    val processCat = for { c <- processesTable if c.id === processId } yield c.processCategory
    db.run(processCat.update(category).map {
      case 0 => Left(ProcessNotFoundError(processId))
      case 1 => Right(())
    })
  }

  private def processTableFilteredByUser(implicit loggedUser: LoggedUser) =
    if (loggedUser.isAdmin) processesTable else processesTable.filter(_.processCategory inSet loggedUser.categories)

  def fetchProcessesDetails()(implicit loggedUser: LoggedUser): Future[List[ProcessDetails]] = {
    fetchProcessesDetailsByQuery(!_.isSubprocess)
  }

  def fetchSubProcessesDetails()(implicit loggedUser: LoggedUser): Future[List[ProcessDetails]] = {
    fetchProcessesDetailsByQuery(_.isSubprocess)
  }

  private def fetchProcessesDetailsByQuery(query: ProcessEntity => Rep[Boolean])
                           (implicit loggedUser: LoggedUser): Future[List[ProcessDetails]] = {
    val action = for {
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
        tagsForProcesses(process.name), List.empty, businessView = false) }
    db.run(action).map(_.toList)
  }

  def fetchLatestProcessDetailsForProcessId(id: String, businessView: Boolean = false)
                                           (implicit loggedUser: LoggedUser): Future[Option[ProcessDetails]] = {
    val action = for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(id).result.headOption)
      processDetails <- fetchProcessDetailsForVersion(latestProcessVersion, isLatestVersion = true, businessView = businessView)
    } yield processDetails
    db.run(action.value)
  }

  def fetchLatestProcessDetailsForProcessIdEither(id: String)
                                                 (implicit loggedUser: LoggedUser): Future[XError[ProcessDetails]] = {
    fetchLatestProcessDetailsForProcessId(id).map {
      case None => Left(ProcessNotFoundError(id))
      case Some(p) => Right(p)
    }
  }

  def fetchProcessDetailsForId(processId: String, versionId: Long, businessView: Boolean)
                              (implicit loggedUser: LoggedUser): Future[Option[ProcessDetails]] = {
    val action = for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(processId).result.headOption)
      processVersion <- OptionT[DB, ProcessVersionEntityData](latestProcessVersions(processId).filter(pv => pv.id === versionId).result.headOption)
      processDetails <- fetchProcessDetailsForVersion(processVersion, isLatestVersion = latestProcessVersion.id == processVersion.id, businessView = businessView)
    } yield processDetails
    db.run(action.value)
  }

  def fetchLatestProcessVersion(processId: String)
                               (implicit loggedUser: LoggedUser): Future[Option[ProcessVersionEntityData]] = {
    val action = latestProcessVersions(processId).result.headOption
    db.run(action)
  }

  private def fetchProcessDetailsForVersion(processVersion: ProcessVersionEntityData, isLatestVersion: Boolean, businessView: Boolean = false)
                                           (implicit loggedUser: LoggedUser) = {
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
    ProcessDetails(
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
    val displayable = ProcessConverter.toDisplayableOrDie(json, process.processingType, businessView = businessView)
    if (businessView) {
      displayable.withSuccessValidation()
    } else {
      displayable.validated(processValidation)
    }
  }

  private def latestProcessVersions(processId: String) = {
    processVersionsTable.filter(_.processId === processId).sortBy(_.createDate.desc)
  }

  private def latestDeployedProcessVersionsPerEnvironment(processId: String) = {
    latestDeployedProcessesVersionsPerEnvironment.filter(_._1._1 === processId).map { case ((_, env), deployedVersion) => (env, deployedVersion)}
  }

  private def latestDeployedProcessesVersionsPerEnvironment = {
    deployedProcessesTable.groupBy(e => (e.processId, e.environment)).map { case (processIdEnv, group) => (processIdEnv, group.map(_.deployedAt).max) }
      .join(deployedProcessesTable).on { case ((processIdEnv, maxDeployedAtForEnv), deplProc) =>
      deplProc.processId === processIdEnv._1 && deplProc.environment === processIdEnv._2 && deplProc.deployedAt === maxDeployedAtForEnv
    }.map { case ((env, _), deployedVersion) => env -> deployedVersion }.filter(_._2.deploymentAction === DeploymentAction.Deploy)
  }

  private def processType(processDeploymentData: ProcessDeploymentData) = processDeploymentData match {
    case a:GraphProcess => ProcessType.Graph
    case a:CustomProcess => ProcessType.Custom
  }


}

object ProcessRepository {

  def apply(db: JdbcBackend.Database,
                          driver: JdbcProfile,
                          processValidation: ProcessValidation, modelData: Map[ProcessingType, ModelData])(implicit ec: ExecutionContext) = {
    val lastMigrations = modelData.mapValues(_.migrations).collect {
      case (k, Some(migrations)) => (k, migrations.processMigrations.keys.max)
    }
    new ProcessRepository(db, driver, processValidation, lastMigrations)
  }

  case class ProcessDetails(
                             id: String,
                             name: String,
                             processVersionId: Long,
                             isLatestVersion: Boolean,
                             description: Option[String],
                             processType: ProcessType,
                             processingType: ProcessingType,
                             processCategory: String,
                             modificationDate: LocalDateTime,
                             tags: List[String],
                             currentlyDeployedAt: Set[String],
                             json: Option[DisplayableProcess],
                             history: List[ProcessHistoryEntry],
                             modelVersion: Option[Int]
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
        deployments = deployedVersionsPerEnv.collect { case (env, deployedVersion) if deployedVersion.processVersionId.contains(processVersion.id) =>
          DeploymentEntry(env, deployedVersion.deployedAtTime,
            deployedVersion.buildInfo.flatMap(BuildInfo.parseJson).getOrElse(BuildInfo.empty))
        }.toList
      )
    }
  }

  case class DeploymentEntry(environment: String, deployedAt: LocalDateTime, buildInfo: Map[String, String])

  case class ProcessNotFoundError(id: String) extends NotFoundError {
    def getMessage = s"No process $id found"
  }

  case class InvalidProcessTypeError(id: String) extends BadRequestError {
    def getMessage = s"Process $id is not GraphProcess"
  }

}