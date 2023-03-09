package pl.touk.nussknacker.ui.process.repository

import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.entity.{CommentActions, ProcessActionEntityData, ProcessActionId}
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.dbio.DBIOAction

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.language.higherKinds

//TODO: Add missing methods: markProcessAsDeployed and markProcessAsCancelled
trait ProcessActionRepository[F[_]] {
  def markProcessAsArchived(processId: ProcessId, processVersion: VersionId)(implicit user: LoggedUser): F[_]
  def markProcessAsUnArchived(processId: ProcessId, processVersion: VersionId)(implicit user: LoggedUser): F[_]
}

object DbProcessActionRepository {
  def create(dbConfig: DbConfig, modelData: ProcessingTypeDataProvider[ModelData])(implicit ec: ExecutionContext): DbProcessActionRepository[DB] =
    new DbProcessActionRepository[DB](dbConfig, modelData.mapValues(_.configCreator.buildInfo())) with DbioRepistory
}

abstract class DbProcessActionRepository[F[_]](val dbConfig: DbConfig, buildInfos: ProcessingTypeDataProvider[Map[String, String]]) (implicit ec: ExecutionContext)
extends Repository[F] with EspTables with CommentActions with ProcessActionRepository[F] with LazyLogging {

  import profile.api._

  def addInProgressAction(processId: ProcessId, actionType: ProcessActionType, processVersion: Option[VersionId], buildInfoProcessingType: Option[ProcessingType])(implicit user: LoggedUser): F[ProcessActionId] = {
    val now = Instant.now()
    run(
      insertAction(None, processId, processVersion = processVersion, actionType = actionType, state = ProcessActionState.InProgress,
      createdAt = now, performedAt = None, failure = None, commentId = None, buildInfoProcessingType = buildInfoProcessingType).map(_.id))
  }

  // We add comment during marking action as finished because we don't want to show this comment for in progress actions
  // Also we pass all other parameters here because in_progress action can be invalidated and we have to revert it back
  def markActionAsFinished(actionId: ProcessActionId, processId: ProcessId, actionType: ProcessActionType, processVersion: VersionId,
                           performedAt: Instant, comment: Option[Comment], buildInfoProcessingType: Option[ProcessingType])
                          (implicit user: LoggedUser): F[Unit] = {
    run(for {
      commentId <- newCommentAction(processId, processVersion, comment)
      updated <- updateAction(actionId, processId, Some(processVersion), ProcessActionState.Finished, Some(performedAt), None, commentId)
      _ <-
        if (updated) {
          DBIOAction.successful(())
        } else {
          // we have to revert action - in progress action was probably invalidated
          insertAction(Some(actionId), processId, Some(processVersion), actionType, ProcessActionState.Finished,
            performedAt, Some(performedAt), None, commentId, buildInfoProcessingType)
        }
    } yield ())
  }

  // We pass all parameters here because in_progress action can be invalidated and we have to revert it back
  def markActionAsFailed(actionId: ProcessActionId, processId: ProcessId, actionType: ProcessActionType, processVersion: Option[VersionId],
                         performedAt: Instant, failure: String, buildInfoProcessingType: Option[ProcessingType])
                        (implicit user: LoggedUser): F[Unit] = {
    run(for {
      updated <- updateAction(actionId, processId, processVersion, ProcessActionState.Failed, Some(performedAt), Some(failure), None)
      _ <-
        if (updated) {
          DBIOAction.successful(())
        } else {
          // we have to revert action - in progress action was probably invalidated
          insertAction(Some(actionId), processId, processVersion, actionType, ProcessActionState.Failed,
            performedAt, Some(performedAt), Some(failure), None, buildInfoProcessingType)
        }
    } yield ())
  }

  def removeAction(actionId: ProcessActionId): F[Unit] = {
    run(processActionsTable.filter(a => a.id === actionId).delete.map(_ => ()))
  }

  override def markProcessAsArchived(processId: ProcessId, processVersion: VersionId)(implicit user: LoggedUser): F[ProcessActionEntityData] =
    addInstantAction(processId, processVersion, ProcessActionType.Archive, None, None)

  override def markProcessAsUnArchived(processId: ProcessId, processVersion: VersionId)(implicit user: LoggedUser): F[ProcessActionEntityData] =
    addInstantAction(processId, processVersion, ProcessActionType.UnArchive, None, None)

  def addInstantAction(processId: ProcessId, processVersion: VersionId, actionType: ProcessActionType, comment: Option[Comment], buildInfoProcessingType: Option[ProcessingType])
                      (implicit user: LoggedUser): F[ProcessActionEntityData] = {
    val now = Instant.now()
    run(for {
      commentId <- newCommentAction(processId, processVersion, comment)
      result <- insertAction(None, processId, Some(processVersion), actionType, ProcessActionState.Finished, now, Some(now), None, commentId, buildInfoProcessingType)
    } yield result)
  }

  private def insertAction(actionIdOpt: Option[ProcessActionId], processId: ProcessId, processVersion: Option[VersionId], actionType: ProcessActionType, state: ProcessActionState,
                           createdAt: Instant, performedAt: Option[Instant], failure: Option[String], commentId: Option[Long], buildInfoProcessingType: Option[ProcessingType])
                          (implicit user: LoggedUser): DB[ProcessActionEntityData] = {
    val actionId = actionIdOpt.getOrElse(ProcessActionId(UUID.randomUUID()))
    val buildInfoJsonOpt = buildInfoProcessingType.flatMap(buildInfos.forType).map(BuildInfo.writeAsJson)
    val processActionData = ProcessActionEntityData(
      id = actionId,
      processId = processId,
      processVersionId = processVersion,
      user = user.username, // TODO: it should be user.id not name
      createdAt = Timestamp.from(createdAt),
      performedAt = performedAt.map(Timestamp.from),
      action = actionType,
      state = state,
      failure = failure,
      commentId = commentId,
      buildInfo = buildInfoJsonOpt)
    (processActionsTable += processActionData).map { insertCount =>
      if (insertCount != 1)
        throw new IllegalArgumentException(s"Action with id: $actionId can't be inserted")
      processActionData
    }
  }

  private def updateAction(actionId: ProcessActionId, processId: ProcessId, processVersion: Option[VersionId], state: ProcessActionState,
                           performedAt: Option[Instant], failure: Option[String], commentId: Option[Long]): DB[Boolean] = {
    for {
      updateCount <- processActionsTable.filter(_.id === actionId)
        .map(a => (a.performedAt, a.state, a.failure, a.commentId))
        .update((performedAt.map(Timestamp.from), state, failure, commentId))
    } yield updateCount == 1
  }

  // we use "select for update where false" query syntax to lock the table - it is useful if you plan to insert something in a critical section
  def lockActionsTable: F[Unit] = {
    run(processActionsTable.filter(_ => false).forUpdate.result.map(_ => ()))
  }

  def getInProgressActionTypes(processId: ProcessId): F[Set[ProcessActionType]] = {
    val query = processActionsTable
      .filter(action => action.processId === processId && action.state === ProcessActionState.InProgress)
      .map(_.action).distinct
    run(query.result.map(_.toSet))
  }

  def getUserActionsAfter(user: LoggedUser, possibleStates: Set[ProcessActionState], actionType: ProcessActionType, limit: Instant): F[Seq[(ProcessActionEntityData, ProcessName)]] = {
    run(
      processActionsTable
        .filter(a => a.user === user.username && a.state.inSet(possibleStates) && a.action === actionType && a.performedAt > Timestamp.from(limit))
        .join(processesTable).on((a, p) => p.id === a.processId)
        .map {
          case (a, p) => (a, p.name)
        }
        .sortBy(_._1.performedAt)
        .result
    )
  }

  def deleteInProgressActions(): F[Unit] = {
    run(processActionsTable.filter(_.state === ProcessActionState.InProgress).delete.map(_ => ()))
  }

}
