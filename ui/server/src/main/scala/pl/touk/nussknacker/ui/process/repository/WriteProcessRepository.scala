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
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.ui.db.entity.{CommentActions, CommentEntityFactory, ProcessEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.ProcessType
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.ProcessShapeFetchStrategy
import pl.touk.nussknacker.ui.process.repository.ProcessRepository._
import pl.touk.nussknacker.ui.process.repository.WriteProcessRepository.UpdateProcessAction
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.DateUtils
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.higherKinds

object WriteProcessRepository {

  def create(dbConfig: DbConfig, modelData: Map[ProcessingType, ModelData]): WriteProcessRepository =

    new DbWriteProcessRepository[Future](dbConfig, modelData.mapValues(_.migrations.version)) with WriteProcessRepository with BasicRepository

  case class UpdateProcessAction(id: ProcessId, deploymentData: ProcessDeploymentData, comment: String)

}

trait WriteProcessRepository {

  def saveNewProcess(processName: ProcessName, category: String, processDeploymentData: ProcessDeploymentData,
                     processingType: ProcessingType, isSubprocess: Boolean)(implicit loggedUser: LoggedUser): Future[XError[Option[ProcessVersionEntityData]]]

  def updateProcess(action: UpdateProcessAction)
                   (implicit loggedUser: LoggedUser): Future[XError[Option[ProcessVersionEntityData]]]

  def updateCategory(processId: ProcessId, category: String)(implicit loggedUser: LoggedUser): Future[XError[Unit]]

  def archive(processId: ProcessId, isArchived: Boolean): Future[XError[Unit]]

  def deleteProcess(processId: ProcessId): Future[XError[Unit]]

  def renameProcess(processId: ProcessId, newName: String): Future[XError[Unit]]
}

abstract class DbWriteProcessRepository[F[_]](val dbConfig: DbConfig,
                                              val modelVersion: Map[ProcessingType, Int])
  extends LazyLogging with ProcessRepository[F] with CommentActions {

  import profile.api._
  
  def saveNewProcess(processName: ProcessName, category: String, processDeploymentData: ProcessDeploymentData,
                     processingType: ProcessingType, isSubprocess: Boolean)
                    (implicit loggedUser: LoggedUser): F[XError[Option[ProcessVersionEntityData]]] = {
    val processToSave = ProcessEntityData(
      id = -1L, name = processName.value, processCategory = category,
      description = None, processType = ProcessType.fromDeploymentData(processDeploymentData),
      processingType = processingType, isSubprocess = isSubprocess, isArchived = false,
      createdAt = DateUtils.toTimestamp(now), createdBy = loggedUser.username)

    val insertNew = processesTable.returning(processesTable.map(_.id)).into { case (entity, newId) => entity.copy(id = newId) }

    val insertAction = logDebug(s"Saving process ${processName.value} by user $loggedUser").flatMap { _ =>
      latestProcessVersionsNoJson(processName).result.headOption.flatMap {
        case Some(_) => DBIOAction.successful(ProcessAlreadyExists(processName.value).asLeft)
        case None => processesTable.filter(_.name === processName.value).result.headOption.flatMap {
          case Some(_) => DBIOAction.successful(ProcessAlreadyExists(processName.value).asLeft)
          case None => (insertNew += processToSave).flatMap(entity => updateProcessInternal(ProcessId(entity.id), processDeploymentData))
        }
      }
    }

    run(insertAction)
  }

  def updateProcess(updateProcessAction: UpdateProcessAction)
                   (implicit loggedUser: LoggedUser): F[XError[Option[ProcessVersionEntityData]]] = {
    val update = updateProcessInternal(updateProcessAction.id, updateProcessAction.deploymentData).flatMap {
      case Right(Some(newVersion)) =>
        newCommentAction(ProcessId(newVersion.processId), newVersion.id, updateProcessAction.comment).map(_ => Right(Some(newVersion)))
      case a => DBIO.successful(a)
    }
    run(update)
  }

  private def updateProcessInternal(processId: ProcessId, processDeploymentData: ProcessDeploymentData)
                                   (implicit loggedUser: LoggedUser): DB[XError[Option[ProcessVersionEntityData]]] = {
    val (maybeJson, maybeMainClass) = processDeploymentData match {
      case GraphProcess(json) => (Some(json), None)
      case CustomProcess(mainClass) => (None, Some(mainClass))
    }

    def versionToInsert(latestProcessVersion: Option[ProcessVersionEntityData],
                        processesVersionCount: Int, processingType: ProcessingType): Option[ProcessVersionEntityData] = latestProcessVersion match {
      case Some(version) if version.json == maybeJson && version.mainClass == maybeMainClass => None
      case _ => Option(ProcessVersionEntityData(id = processesVersionCount + 1, processId = processId.value,
        json = maybeJson, mainClass = maybeMainClass, createDate = DateUtils.toTimestamp(now),
        user = loggedUser.username, modelVersion = modelVersion.get(processingType)))
    }

    //TODO: why EitherT.right doesn't infere properly here?
    def rightT[T](value: DB[T]): EitherT[DB, EspError, T] = EitherT[DB, EspError, T](value.map(Right(_)))

    val insertAction = for {
      _ <- rightT(logDebug(s"Updating process $processId by user $loggedUser"))
      maybeProcess <- rightT(processTableFilteredByUser.filter(_.id === processId.value).result.headOption)
      process <- EitherT.fromEither[DB](Either.fromOption(maybeProcess, ProcessNotFoundError(processId.value.toString)))
      _ <- EitherT.fromEither(Either.cond(process.processType == ProcessType.fromDeploymentData(processDeploymentData), (), InvalidProcessTypeError(processId.value.toString)))
      processesVersionCount <- rightT(processVersionsTableNoJson.filter(p => p.processId === processId.value).length.result)
      latestProcessVersion <- rightT(fetchProcessLatestVersions(processId)(ProcessShapeFetchStrategy.FetchDisplayable).result.headOption)
      newProcessVersion <- EitherT.fromEither(Right(versionToInsert(latestProcessVersion, processesVersionCount, process.processingType)))
      _ <- EitherT.right[EspError](newProcessVersion.map(processVersionsTable += _).getOrElse(dbMonad.pure(0)))
    } yield newProcessVersion
    insertAction.value
  }

  private def logDebug(s: String) = {
    dbMonad.pure(()).map(_ => logger.debug(s))
  }

  def deleteProcess(processId: ProcessId): F[XError[Unit]] = {
    val action: DB[XError[Unit]] = processesTable.filter(_.id === processId.value).delete.map {
      case 0 => Left(ProcessNotFoundError(processId.value.toString))
      case 1 => Right(())
    }
    run(action)
  }

  def archive(processId: ProcessId, isArchived: Boolean): F[XError[Unit]] = {
    val isArchivedQuery = for {c <- processesTable if c.id === processId.value} yield c.isArchived
    val action: DB[XError[Unit]] = isArchivedQuery.update(isArchived).map {
      case 0 => Left(ProcessNotFoundError(processId.value.toString))
      case 1 => Right(())
    }
    run(action)
  }

  //accessible only from initializing scripts so far
  def updateCategory(processId: ProcessId, category: String)(implicit loggedUser: LoggedUser): F[XError[Unit]] = {
    val processCat = for {c <- processesTable if c.id === processId.value} yield c.processCategory
    val action: DB[XError[Unit]] = processCat.update(category).map {
      case 0 => Left(ProcessNotFoundError(processId.value.toString))
      case 1 => Right(())
    }
    run(action)
  }

  def renameProcess(processId: ProcessId, newName: String): F[XError[Unit]] = {
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