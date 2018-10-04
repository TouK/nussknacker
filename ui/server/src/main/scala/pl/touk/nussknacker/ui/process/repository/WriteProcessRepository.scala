package pl.touk.nussknacker.ui.process.repository

import java.sql.Timestamp
import java.time.LocalDateTime

import cats.data._
import cats.syntax.either._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.EspError._
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.db.EspTables._
import pl.touk.nussknacker.ui.db.entity.CommentEntity
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.{ProcessEntityData, ProcessType}
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessRepository._
import pl.touk.nussknacker.ui.process.repository.WriteProcessRepository.UpdateProcessAction
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.DateUtils
import pl.touk.nussknacker.ui.validation.ProcessValidation
import slick.dbio.DBIOAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.higherKinds

object WriteProcessRepository {

  def create(dbConfig: DbConfig, modelData: Map[ProcessingType, ModelData]) : WriteProcessRepository =

    new DbWriteProcessRepository[Future](dbConfig, modelData.mapValues(_.migrations.version)) with WriteProcessRepository with BasicRepository

  case class UpdateProcessAction(id: String, deploymentData: ProcessDeploymentData, comment: String)
}
trait WriteProcessRepository {

  def saveNewProcess(processId: String, category: String, processDeploymentData: ProcessDeploymentData,
                     processingType: ProcessingType, isSubprocess: Boolean)(implicit loggedUser: LoggedUser): Future[XError[Unit]]

  def updateProcess(action: UpdateProcessAction)
                   (implicit loggedUser: LoggedUser): Future[XError[Option[ProcessVersionEntityData]]]

  def updateCategory(processId: String, category: String)(implicit loggedUser: LoggedUser): Future[XError[Unit]]

  def archive(processId: String, isArchived: Boolean): Future[XError[Unit]]

  def deleteProcess(processId: String): Future[XError[Unit]]

  def renameProcess(processId: String, newName: String): Future[XError[Unit]]
}

abstract class DbWriteProcessRepository[F[_]](val dbConfig: DbConfig,
                                     val modelVersion: Map[ProcessingType, Int]) extends LazyLogging with ProcessRepository[F] {
  
  import driver.api._

  def saveNewProcess(processId: String, category: String, processDeploymentData: ProcessDeploymentData,
                     processingType: ProcessingType, isSubprocess: Boolean)
                    (implicit loggedUser: LoggedUser): F[XError[Unit]] = {
    // todo: initial process name == processId?
    val processName = processId

    val processToSave = ProcessEntityData(id = processId, name = processName, processCategory = category,
      description = None, processType = ProcessType.fromDeploymentData(processDeploymentData),
      processingType = processingType, isSubprocess = isSubprocess, isArchived = false)

    val insertAction = logInfo(s"Saving process $processId by user $loggedUser").flatMap { _ =>
      latestProcessVersions(processId).result.headOption.flatMap {
        case Some(_) => DBIOAction.successful(ProcessAlreadyExists(processId).asLeft)
        case None => processesTable.filter(_.name === processName).result.headOption.flatMap {
          case Some(_) => DBIOAction.successful(ProcessAlreadyExists(processName).asLeft)
          case None => (processesTable += processToSave).andThen(updateProcessInternal(processId, processDeploymentData))
        }
      }.map(_.map(_ => ()))
    }

    run(insertAction)
  }

  def updateProcess(updateProcessAction: UpdateProcessAction)
                   (implicit loggedUser: LoggedUser): F[XError[Option[ProcessVersionEntityData]]] = {
    val update = updateProcessInternal(updateProcessAction.id, updateProcessAction.deploymentData).flatMap {
      case Right(Some(newVersion)) =>
        CommentEntity.newCommentAction(newVersion.processId, newVersion.id, updateProcessAction.comment).map(_ => Right(Some(newVersion)))
      case a => DBIO.successful(a)
    }
    run(update)
  }

  private def updateProcessInternal(processId: String, processDeploymentData: ProcessDeploymentData)
                                   (implicit loggedUser: LoggedUser): DB[XError[Option[ProcessVersionEntityData]]] = {
    val (maybeJson, maybeMainClass) = processDeploymentData match {
      case GraphProcess(json) => (Some(json), None)
      case CustomProcess(mainClass) => (None, Some(mainClass))
    }

    def versionToInsert(latestProcessVersion: Option[ProcessVersionEntityData],
                        processesVersionCount: Int, processingType: ProcessingType): Option[ProcessVersionEntityData] = latestProcessVersion match {
      case Some(version) if version.json == maybeJson && version.mainClass == maybeMainClass => None
      case _ => Option(ProcessVersionEntityData(id = processesVersionCount + 1, processId = processId,
        json = maybeJson, mainClass = maybeMainClass, createDate = DateUtils.toTimestamp(now),
        user = loggedUser.id, modelVersion = modelVersion.get(processingType)))
    }

    //TODO: why EitherT.right doesn't infere properly here?
    def rightT[T](value: DB[T]): EitherT[DB, EspError, T] = EitherT[DB, EspError, T](value.map(Right(_)))

    val insertAction = for {
      _ <- rightT(logInfo(s"Updating process $processId by user $loggedUser"))
      maybeProcess <- rightT(processTableFilteredByUser.filter(_.id === processId).result.headOption)
      process <- EitherT.fromEither[DB](Either.fromOption(maybeProcess, ProcessNotFoundError(processId)))
      _ <- EitherT.fromEither(Either.cond(process.processType == ProcessType.fromDeploymentData(processDeploymentData), (), InvalidProcessTypeError(processId)))
      processesVersionCount <- rightT(processVersionsTable.filter(p => p.processId === processId).length.result)
      latestProcessVersion <- rightT(latestProcessVersions(processId).result.headOption)
      newProcessVersion <- EitherT.fromEither(Right(versionToInsert(latestProcessVersion, processesVersionCount, process.processingType)))
      _ <- EitherT.right[EspError](newProcessVersion.map(processVersionsTable += _).getOrElse(dbMonad.pure(0)))
    } yield newProcessVersion
    insertAction.value
  }

  private def logInfo(s: String) = {
    dbMonad.pure(()).map(_ => logger.info(s))
  }

  def deleteProcess(processId: String): F[XError[Unit]] = {
    val action : DB[XError[Unit]] = processesTable.filter(_.id === processId).delete.map {
      case 0 => Left(ProcessNotFoundError(processId))
      case 1 => Right(())
    }
    run(action)
  }
  def archive(processId: String, isArchived: Boolean): F[XError[Unit]] ={
    val isArchivedQuery = for {c <- processesTable if c.id === processId} yield c.isArchived
    val action  : DB[XError[Unit]] = isArchivedQuery.update(isArchived).map {
      case 0 => Left(ProcessNotFoundError(processId))
      case 1 => Right(())
    }
    run(action)
  }

  //accessible only from initializing scripts so far
  def updateCategory(processId: String, category: String)(implicit loggedUser: LoggedUser): F[XError[Unit]] = {
    val processCat = for {c <- processesTable if c.id === processId} yield c.processCategory
    val action  : DB[XError[Unit]] = processCat.update(category).map {
      case 0 => Left(ProcessNotFoundError(processId))
      case 1 => Right(())
    }
    run(action)
  }

  def renameProcess(processId: String, newName: String): F[XError[Unit]] = {
    def updateNameInSingleProcessVersion(processVersion: ProcessVersionEntityData, process: ProcessEntityData) = {
      processVersion.json match {
        case Some(json) =>
          val updatedJson = ProcessConverter.modify(json, process.processingType)(_.copy(id = newName))
          val updatedProcessVersion = processVersion.copy(json = Some(updatedJson))
          processVersionsTable.filter(version => version.id === processVersion.id && version.processId === process.id)
            .update(updatedProcessVersion)
        case None => DBIO.successful(())
      }
    }

    val updateNameInProcessJson =
      processVersionsTable.filter(_.processId === processId.value)
        .join(processesTable)
        .on { case (version, process) => version.processId === process.id }
        .result.flatMap { processVersions =>
          DBIO.seq(processVersions.map((updateNameInSingleProcessVersion _).tupled): _*)
        }

    val updateNameInProcess =
      processesTable.filter(_.id === processId.value).map(_.name).update(newName)

    val action = processesTable.filter(_.name === newName).result.headOption.flatMap {
      case Some(_) => DBIO.successful(ProcessAlreadyExists(newName).asLeft)
      case None =>
        DBIO.seq[Effect.All](
          updateNameInProcess,
          updateNameInProcessJson
        ).map(_ => ().asRight).transactionally
    }

    run(action)
  }

  //to override in tests
  protected def now: LocalDateTime = LocalDateTime.now()

}