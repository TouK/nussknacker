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
import pl.touk.nussknacker.restmodel.process.{ProcessIdWithName, ProcessingType}
import pl.touk.nussknacker.restmodel.processdetails.ProcessShapeFetchStrategy
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository._
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{CreateProcessAction, ProcessCreated, ProcessUpdated, UpdateProcessAction}
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

  case class ProcessUpdated(processId: ProcessId, oldVersion: Option[VersionId], newVersion: Option[VersionId])

  case class ProcessCreated(processId: ProcessId, processVersionId: VersionId)
}

trait ProcessRepository[F[_]] {

  def saveNewProcess(action: CreateProcessAction)(implicit loggedUser: LoggedUser): F[XError[Option[ProcessCreated]]]

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
  def saveNewProcess(action: CreateProcessAction)(implicit loggedUser: LoggedUser): DB[XError[Option[ProcessCreated]]] = {
    val processToSave = ProcessEntityData(
      id = ProcessId(-1L), name = action.processName.value, processCategory = action.category, description = None,
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
            .flatMap(entity => updateProcessInternal(entity.id, action.graphProcess, false))
            .map(_.right.map(res => res.newVersion.map(ProcessCreated(res.processId, _))))
        }
      }
    }

    insertAction
  }

  def updateProcess(updateProcessAction: UpdateProcessAction)(implicit loggedUser: LoggedUser): DB[XError[ProcessUpdated]] = {
    def addNewCommentToVersion(processId: ProcessId, versionId: VersionId) = {
      newCommentAction(processId, versionId, updateProcessAction.comment)
    }

    updateProcessInternal(updateProcessAction.id, updateProcessAction.graphProcess, updateProcessAction.increaseVersionWhenJsonNotChanged).flatMap {
      // Comment should be added via ProcessService not to mix this repository responsibility.
      case updateProcessRes@Right(ProcessUpdated(processId, _, Some(newVersion))) =>
        addNewCommentToVersion(processId, newVersion).map(_ => updateProcessRes)
      case updateProcessRes@Right(ProcessUpdated(processId, Some(oldVersion), _)) =>
        addNewCommentToVersion(processId, oldVersion).map(_ => updateProcessRes)
      case a => DBIO.successful(a)
    }
  }

  private def updateProcessInternal(processId: ProcessId, graphProcess: GraphProcess, increaseVersionWhenJsonNotChanged: Boolean)(implicit loggedUser: LoggedUser): DB[XError[ProcessUpdated]] = {
    def createProcessVersionEntityData(version: VersionId, processingType: ProcessingType) = ProcessVersionEntityData(
      id = version, processId = processId, json = Some(graphProcess.marshalled), createDate = Timestamp.from(now),
      user = loggedUser.username, modelVersion = modelVersion.forType(processingType)
    )

    //We compare Json representation to ignore formatting differences
    def isLastVersionContainsSameJson(lastVersion: ProcessVersionEntityData): Boolean =
      lastVersion.json.map(GraphProcess.apply).contains(graphProcess)

    //TODO: after we move Json type to GraphProcess we should clean up this pattern matching
    def versionToInsert(latestProcessVersion: Option[ProcessVersionEntityData], processingType: ProcessingType) =
      Right(latestProcessVersion match {
        case Some(version) if isLastVersionContainsSameJson(version) && !increaseVersionWhenJsonNotChanged =>
          None
        case Some(version) =>
          Some(createProcessVersionEntityData(version.id.increase, processingType))
        case _ =>
          Some(createProcessVersionEntityData(VersionId.initialVersionId, processingType))
      })

    //TODO: why EitherT.right doesn't infere properly here?
    def rightT[T](value: DB[T]): EitherT[DB, EspError, T] = EitherT[DB, EspError, T](value.map(Right(_)))

    val insertAction = for {
      _ <- rightT(logDebug(s"Updating scenario $processId by user $loggedUser"))
      maybeProcess <- rightT(processTableFilteredByUser.filter(_.id === processId).result.headOption)
      process <- EitherT.fromEither[DB](Either.fromOption(maybeProcess, ProcessNotFoundError(processId.value.toString)))
      latestProcessVersion <- rightT(fetchProcessLatestVersionsQuery(processId)(ProcessShapeFetchStrategy.FetchDisplayable).result.headOption)
      newProcessVersion <- EitherT.fromEither(versionToInsert(latestProcessVersion, process.processingType))
      _ <- EitherT.right[EspError](newProcessVersion.map(processVersionsTable += _).getOrElse(dbMonad.pure(0)))
    } yield ProcessUpdated(process.id, oldVersion = latestProcessVersion.map(_.id), newVersion = newProcessVersion.map(_.id))
    insertAction.value
  }

  private def logDebug(s: String) = {
    dbMonad.pure(()).map(_ => logger.debug(s))
  }

  def deleteProcess(processId: ProcessId): DB[XError[Unit]] =
    processesTable.filter(_.id === processId).delete.map {
      case 0 => Left(ProcessNotFoundError(processId.value.toString))
      case 1 => Right(())
    }

  def archive(processId: ProcessId, isArchived: Boolean): DB[XError[Unit]] =
    processesTable.filter(_.id === processId).map(_.isArchived).update(isArchived).map {
      case 0 => Left(ProcessNotFoundError(processId.value.toString))
      case 1 => Right(())
    }

  //accessible only from initializing scripts so far
  def updateCategory(processId: ProcessId, category: String)(implicit loggedUser: LoggedUser): DB[XError[Unit]] =
    processesTable.filter(_.id === processId).map(_.processCategory).update(category).map {
      case 0 => Left(ProcessNotFoundError(processId.value.toString))
      case 1 => Right(())
    }

  def renameProcess(process: ProcessIdWithName, newName: String)(implicit loggedUser: LoggedUser): DB[XError[Unit]] = {
    def updateNameInSingleProcessVersion(processVersion: ProcessVersionEntityData, process: ProcessEntityData) = {
      processVersion.json match {
        case Some(json) =>
          val updatedJson = ProcessConverter.modify(GraphProcess(json), process.processingType)(_.copy(id = newName))
          val updatedProcessVersion = processVersion.copy(json = Some(updatedJson))
          processVersionsTable.filter(version => version.id === processVersion.id && version.processId === process.id)
            .update(updatedProcessVersion)
        case None => DBIO.successful(())
      }
    }

    val updateNameInProcessJson =
      processVersionsTable.filter(_.processId === process.id)
        .join(processesTable)
        .on { case (version, process) => version.processId === process.id }
        .result.flatMap { processVersions =>
        DBIO.seq(processVersions.map((updateNameInSingleProcessVersion _).tupled): _*)
      }

    val updateNameInProcess =
      processesTable.filter(_.id === process.id).map(_.name).update(newName)

    // Comment relates to specific version (in this case last version). Last version could be extracted in one of the
    // above queries, but for sake of readability we perform separate query for this matter
    // todo: remove this comment in favour of process-audit-log
    val addCommentAction = processVersionsTable
      .filter(_.processId === process.id)
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

  //We use it only on tests..
  def changeVersionId(processId: ProcessId, versionId: VersionId, versionIdToUpdate: VersionId) =
    processVersionsTableNoJson
      .filter(v => v.id === versionId && v.processId === processId)
      .map(_.id)
      .update(versionIdToUpdate)
}
