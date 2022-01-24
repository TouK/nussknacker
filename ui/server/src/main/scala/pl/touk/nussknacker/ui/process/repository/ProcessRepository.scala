package pl.touk.nussknacker.ui.process.repository

import java.time.Instant
import cats.data._
import cats.syntax.either._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.EspError._
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.ui.db.entity.{CommentActions, ProcessEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.restmodel.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.restmodel.processdetails.ProcessShapeFetchStrategy
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository._
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{CreateProcessAction, ProcessUpdated, UpdateProcessAction}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.dbio.DBIOAction

import java.sql.Timestamp
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.higherKinds

object ProcessRepository {

  def create(dbConfig: DbConfig, modelData: ProcessingTypeDataProvider[ModelData]): DBProcessRepository =
    new DBProcessRepository(dbConfig, modelData.mapValues(_.migrations.version))

  case class UpdateProcessAction(id: ProcessId, graphProcess: GraphProcess, comment: String, increaseVersionWhenJsonNotChanged: Boolean)

  case class CreateProcessAction(processName: ProcessName, category: String, graphProcess: GraphProcess, processingType: ProcessingType, isSubprocess: Boolean)

  case class ProcessUpdated(oldVersion: Option[ProcessVersionEntityData], newVersion: Option[ProcessVersionEntityData])
}

trait ProcessRepository[F[_]] {

  def saveNewProcess(action: CreateProcessAction)(implicit loggedUser: LoggedUser): F[XError[Option[ProcessVersionEntityData]]]

  def updateProcess(action: UpdateProcessAction)(implicit loggedUser: LoggedUser): F[XError[ProcessUpdated]]

  def updateCategory(processId: ProcessId, category: String)(implicit loggedUser: LoggedUser): F[XError[Unit]]

  def archive(processId: ProcessId, isArchived: Boolean): F[XError[Unit]]

  def deleteProcess(processId: ProcessId): F[XError[Unit]]

  def renameProcess(processId: ProcessIdWithName, newName: String)(implicit loggedUser: LoggedUser): F[XError[Unit]]
}

class DBProcessRepository(val dbConfig: DbConfig, val modelVersion: ProcessingTypeDataProvider[Int])
  extends ProcessRepository[DB] with EspTables with LazyLogging with CommentActions with ProcessDBQueryRepository[DB] {

  import profile.api._

  // FIXME: It's temporary way.. After merge and refactor process repositories we can remove it.
  override def run[R]: DB[R] => DB[R] = identity

  /**
    * These action should be done on transaction - move it to ProcessService.createProcess
    */
  def saveNewProcess(action: CreateProcessAction)(implicit loggedUser: LoggedUser): DB[XError[Option[ProcessVersionEntityData]]] = {
    val processToSave = ProcessEntityData(
      id = -1L, name = action.processName.value, processCategory = action.category, description = None,
      processingType = action.processingType, isSubprocess = action.isSubprocess, isArchived = false,
      createdAt = Timestamp.from(now), createdBy = loggedUser.username
    )

    val insertNew = processesTable.returning(processesTable.map(_.id)).into { case (entity, newId) => entity.copy(id = newId) }

    val insertAction = logDebug(s"Saving scenario ${action.processName.value} by user $loggedUser").flatMap { _ =>
      latestProcessVersionsNoJsonQuery(action.processName).result.headOption.flatMap {
        case Some(_) => DBIOAction.successful(ProcessAlreadyExists(action.processName.value).asLeft)
        case None => processesTable.filter(_.name === action.processName.value).result.headOption.flatMap {
          case Some(_) => DBIOAction.successful(ProcessAlreadyExists(action.processName.value).asLeft)
          case None => (insertNew += processToSave)
            .flatMap(entity => updateProcessInternal(ProcessId(entity.id), action.graphProcess, false))
            .map(_.right.map(_.newVersion))
        }
      }
    }

    insertAction
  }

  def updateProcess(updateProcessAction: UpdateProcessAction)(implicit loggedUser: LoggedUser): DB[XError[ProcessUpdated]] = {
    def addNewCommentToVersion(version: ProcessVersionEntityData) = {
      newCommentAction(version.processId, version.id, updateProcessAction.comment)
    }

    updateProcessInternal(updateProcessAction.id, updateProcessAction.graphProcess, updateProcessAction.increaseVersionWhenJsonNotChanged).flatMap {
      // Comment should be added via ProcessService not to mix this repository responsibility.
      case updateProcessRes@Right(ProcessUpdated(_, Some(newVersion))) =>
        addNewCommentToVersion(newVersion).map(_ => updateProcessRes)
      case updateProcessRes@Right(ProcessUpdated(Some(oldVersion), _)) =>
        addNewCommentToVersion(oldVersion).map(_ => updateProcessRes)
      case a => DBIO.successful(a)
    }
  }

  private def updateProcessInternal(processId: ProcessId, graphProcess: GraphProcess, increaseVersionWhenJsonNotChanged: Boolean)(implicit loggedUser: LoggedUser): DB[XError[ProcessUpdated]] = {
    def createProcessVersionEntityData(id: VersionId, processingType: ProcessingType) = ProcessVersionEntityData(
      id = id.increase, processId = processId, json = Some(graphProcess.toString), createDate = Timestamp.from(now),
      user = loggedUser.username, modelVersion = modelVersion.forType(processingType)
    )

    //We compare Json representation to ignore formatting differences
    def isLastVersionContainsSameJson(lastVersion: ProcessVersionEntityData): Boolean =
      lastVersion.graphProcess.equals(graphProcess)

    //TODO: after we move Json type to GraphProcess we should clean up this pattern matching
    def versionToInsert(latestProcessVersion: Option[ProcessVersionEntityData], processingType: ProcessingType) =
      latestProcessVersion match {
        case Some(version) if isLastVersionContainsSameJson(version) && !increaseVersionWhenJsonNotChanged =>
          Right(None)
        case versionOpt =>
          Right(Option(createProcessVersionEntityData(versionOpt.map(_.id).getOrElse(VersionId.initialVersionId), processingType)))
      }

    //TODO: why EitherT.right doesn't infere properly here?
    def rightT[T](value: DB[T]): EitherT[DB, EspError, T] = EitherT[DB, EspError, T](value.map(Right(_)))

    val insertAction = for {
      _ <- rightT(logDebug(s"Updating scenario $processId by user $loggedUser"))
      maybeProcess <- rightT(processTableFilteredByUser.filter(_.id === processId.value).result.headOption)
      process <- EitherT.fromEither[DB](Either.fromOption(maybeProcess, ProcessNotFoundError(processId.value.toString)))
      latestProcessVersion <- rightT(fetchProcessLatestVersionsQuery(processId)(ProcessShapeFetchStrategy.FetchDisplayable).result.headOption)
      newProcessVersion <- EitherT.fromEither(versionToInsert(latestProcessVersion, process.processingType))
      _ <- EitherT.right[EspError](newProcessVersion.map(processVersionsTable += _).getOrElse(dbMonad.pure(0)))
    } yield ProcessUpdated(oldVersion = latestProcessVersion, newVersion = newProcessVersion)
    insertAction.value
  }

  private def logDebug(s: String) = {
    dbMonad.pure(()).map(_ => logger.debug(s))
  }

  def deleteProcess(processId: ProcessId): DB[XError[Unit]] =
    processesTable.filter(_.id === processId.value).delete.map {
      case 0 => Left(ProcessNotFoundError(processId.value.toString))
      case 1 => Right(())
    }

  def archive(processId: ProcessId, isArchived: Boolean): DB[XError[Unit]] =
    processesTable.filter(_.id === processId.value).map(_.isArchived).update(isArchived).map {
      case 0 => Left(ProcessNotFoundError(processId.value.toString))
      case 1 => Right(())
    }

  //accessible only from initializing scripts so far
  def updateCategory(processId: ProcessId, category: String)(implicit loggedUser: LoggedUser): DB[XError[Unit]] =
    processesTable.filter(_.id === processId.value).map(_.processCategory).update(category).map {
      case 0 => Left(ProcessNotFoundError(processId.value.toString))
      case 1 => Right(())
    }

  def renameProcess(process: ProcessIdWithName, newName: String)(implicit loggedUser: LoggedUser): DB[XError[Unit]] = {
    def updateNameInSingleProcessVersion(processVersion: ProcessVersionEntityData, process: ProcessEntityData) = {
      processVersion.json match {
        case Some(_) =>
          val updatedJson = ProcessConverter.modify(processVersion.graphProcess, process.processingType)(_.copy(id = newName))
          val updatedProcessVersion = processVersion.copy(json = Some(updatedJson))
          processVersionsTable.filter(version => version.id === processVersion.id.value && version.processId === process.id)
            .update(updatedProcessVersion)
        case None => DBIO.successful(())
      }
    }

    val updateNameInProcessJson =
      processVersionsTable.filter(_.processId === process.id.value)
        .join(processesTable)
        .on { case (version, process) => version.processId === process.id }
        .result.flatMap { processVersions =>
        DBIO.seq(processVersions.map((updateNameInSingleProcessVersion _).tupled): _*)
      }

    val updateNameInProcess =
      processesTable.filter(_.id === process.id.value).map(_.name).update(newName)

    // Comment relates to specific version (in this case last version). Last version could be extracted in one of the
    // above queries, but for sake of readability we perform separate query for this matter
    // todo: remove this comment in favour of process-audit-log
    val addCommentAction = processVersionsTable
      .filter(_.processId === process.id.value)
      .sortBy(_.id.desc)
      .result.headOption.flatMap {
      case Some(version) => newCommentAction(process.id, version.id, s"Rename: [${process.name.value}] -> [$newName]")
      case None =>  DBIO.successful(())
    }

    val action = processesTable.filter(_.name === newName).result.headOption.flatMap {
      case Some(_) => DBIO.successful(ProcessAlreadyExists(newName).asLeft)
      case None =>
        DBIO.seq[Effect.All](
          updateNameInProcess,
          updateNameInProcessJson,
          addCommentAction
        ).map(_ => ().asRight).transactionally
    }

    action
  }

  //to override in tests
  protected def now: Instant = Instant.now()
}
