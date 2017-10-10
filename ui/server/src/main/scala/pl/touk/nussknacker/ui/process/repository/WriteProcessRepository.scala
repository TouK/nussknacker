package pl.touk.nussknacker.ui.process.repository

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
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.{ProcessEntityData, ProcessType}
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
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

  def deleteProcess(processId: String): Future[XError[Unit]]

}

abstract class DbWriteProcessRepository[F[_]](val dbConfig: DbConfig,
                                     val modelVersion: Map[ProcessingType, Int]) extends LazyLogging with ProcessRepository[F] {
  
  import driver.api._

  def saveNewProcess(processId: String, category: String, processDeploymentData: ProcessDeploymentData,
                     processingType: ProcessingType, isSubprocess: Boolean)
                    (implicit loggedUser: LoggedUser): F[XError[Unit]] = {
    logger.info(s"Saving process $processId by user $loggedUser")

    val processToSave = ProcessEntityData(id = processId, name = processId, processCategory = category,
      description = None, processType = ProcessType.fromDeploymentData(processDeploymentData),
      processingType = processingType, isSubprocess = isSubprocess)

    val insertAction = latestProcessVersions(processId).result.headOption.flatMap {
      case Some(_) => DBIOAction.successful(Left(ProcessAlreadyExists(processId)))
      case None => (processesTable += processToSave).andThen(updateProcessInternal(processId, processDeploymentData))
    }.map(_.map(_ => ()))

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
      _ <- EitherT.fromEither(Either.cond(process.processType == ProcessType.fromDeploymentData(processDeploymentData), (), InvalidProcessTypeError(processId)))
      processesVersionCount <- EitherT.right[DB, EspError, Int](processVersionsTable.filter(p => p.processId === processId).length.result)
      latestProcessVersion <- EitherT.right[DB, EspError, Option[ProcessVersionEntityData]](latestProcessVersions(processId).result.headOption)
      newProcessVersion <- EitherT.fromEither(Right(versionToInsert(latestProcessVersion, processesVersionCount, process.processingType)))
      _ <- EitherT.right[DB, EspError, Int](newProcessVersion.map(processVersionsTable += _).getOrElse(dbMonad.pure(0)))
    } yield newProcessVersion
    insertAction.value
  }

  def deleteProcess(processId: String): F[XError[Unit]] = {
    val action : DB[XError[Unit]] = processesTable.filter(_.id === processId).delete.map {
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

}