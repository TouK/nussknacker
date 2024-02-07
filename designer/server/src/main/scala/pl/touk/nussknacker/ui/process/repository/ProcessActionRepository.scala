package pl.touk.nussknacker.ui.process.repository

import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionId, ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, ProcessingType, VersionId}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.entity.{CommentActions, CommentEntityData, ProcessActionEntityData}
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.dbio.DBIOAction

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

//TODO: Add missing methods: markProcessAsDeployed and markProcessAsCancelled
trait ProcessActionRepository {
  def markProcessAsArchived(processId: ProcessId, processVersion: VersionId)(implicit user: LoggedUser): DB[_]
  def markProcessAsUnArchived(processId: ProcessId, processVersion: VersionId)(implicit user: LoggedUser): DB[_]
  def getFinishedProcessAction(actionId: ProcessActionId)(implicit ec: ExecutionContext): DB[Option[ProcessAction]]

  def getFinishedProcessActions(processId: ProcessId, actionTypesOpt: Option[Set[ProcessActionType]])(
      implicit ec: ExecutionContext
  ): DB[List[ProcessAction]]

  def getLastActionPerProcess(
      actionState: Set[ProcessActionState],
      actionTypesOpt: Option[Set[ProcessActionType]]
  ): DB[Map[ProcessId, ProcessAction]]

}

class DbProcessActionRepository(
    protected val dbRef: DbRef,
    buildInfos: ProcessingTypeDataProvider[Map[String, String], _]
)(implicit ec: ExecutionContext)
    extends DbioRepository
    with NuTables
    with CommentActions
    with ProcessActionRepository
    with LazyLogging {

  import profile.api._

  def addInProgressAction(
      processId: ProcessId,
      actionType: ProcessActionType,
      processVersion: Option[VersionId],
      buildInfoProcessingType: Option[ProcessingType]
  )(implicit user: LoggedUser): DB[ProcessActionId] = {
    val now = Instant.now()
    run(
      insertAction(
        None,
        processId,
        processVersion = processVersion,
        actionType = actionType,
        state = ProcessActionState.InProgress,
        createdAt = now,
        performedAt = None,
        failure = None,
        commentId = None,
        buildInfoProcessingType = buildInfoProcessingType
      ).map(_.id)
    )
  }

  // We add comment during marking action as finished because we don't want to show this comment for in progress actions
  // Also we pass all other parameters here because in_progress action can be invalidated and we have to revert it back
  def markActionAsFinished(
      actionId: ProcessActionId,
      processId: ProcessId,
      actionType: ProcessActionType,
      processVersion: VersionId,
      performedAt: Instant,
      comment: Option[Comment],
      buildInfoProcessingType: Option[ProcessingType]
  )(implicit user: LoggedUser): DB[Unit] = {
    run(for {
      comment <- newCommentAction(processId, processVersion, comment)
      updated <- updateAction(actionId, ProcessActionState.Finished, Some(performedAt), None, comment.map(_.id))
      _ <-
        if (updated) {
          DBIOAction.successful(())
        } else {
          // we have to revert action - in progress action was probably invalidated
          insertAction(
            Some(actionId),
            processId,
            Some(processVersion),
            actionType,
            ProcessActionState.Finished,
            performedAt,
            Some(performedAt),
            None,
            comment.map(_.id),
            buildInfoProcessingType
          )
        }
    } yield ())
  }

  // We pass all parameters here because in_progress action can be invalidated and we have to revert it back
  def markActionAsFailed(
      actionId: ProcessActionId,
      processId: ProcessId,
      actionType: ProcessActionType,
      processVersion: Option[VersionId],
      performedAt: Instant,
      failureMessage: String,
      buildInfoProcessingType: Option[ProcessingType]
  )(implicit user: LoggedUser): DB[Unit] = {
    val failureMessageOpt = Option(failureMessage).map(_.take(1022)) // crop to not overflow column size)
    run(for {
      updated <- updateAction(actionId, ProcessActionState.Failed, Some(performedAt), failureMessageOpt, None)
      _ <-
        if (updated) {
          DBIOAction.successful(())
        } else {
          // we have to revert action - in progress action was probably invalidated
          insertAction(
            Some(actionId),
            processId,
            processVersion,
            actionType,
            ProcessActionState.Failed,
            performedAt,
            Some(performedAt),
            failureMessageOpt,
            None,
            buildInfoProcessingType
          )
        }
    } yield ())
  }

  def markFinishedActionAsExecutionFinished(actionId: ProcessActionId): DB[Boolean] = {
    run(
      processActionsTable
        .filter(a => a.id === actionId && a.state === ProcessActionState.Finished)
        .map(_.state)
        .update(ProcessActionState.ExecutionFinished)
        .map(_ == 1)
    )
  }

  def removeAction(actionId: ProcessActionId): DB[Unit] = {
    run(processActionsTable.filter(a => a.id === actionId).delete.map(_ => ()))
  }

  override def markProcessAsArchived(processId: ProcessId, processVersion: VersionId)(
      implicit user: LoggedUser
  ): DB[ProcessAction] =
    addInstantAction(processId, processVersion, ProcessActionType.Archive, None, None)

  override def markProcessAsUnArchived(processId: ProcessId, processVersion: VersionId)(
      implicit user: LoggedUser
  ): DB[ProcessAction] =
    addInstantAction(processId, processVersion, ProcessActionType.UnArchive, None, None)

  def addInstantAction(
      processId: ProcessId,
      processVersion: VersionId,
      actionType: ProcessActionType,
      comment: Option[Comment],
      buildInfoProcessingType: Option[ProcessingType]
  )(implicit user: LoggedUser): DB[ProcessAction] = {
    val now = Instant.now()
    run(for {
      comment <- newCommentAction(processId, processVersion, comment)
      result <- insertAction(
        None,
        processId,
        Some(processVersion),
        actionType,
        ProcessActionState.Finished,
        now,
        Some(now),
        None,
        comment.map(_.id),
        buildInfoProcessingType
      )
    } yield toFinishedProcessAction(result, comment))
  }

  private def insertAction(
      actionIdOpt: Option[ProcessActionId],
      processId: ProcessId,
      processVersion: Option[VersionId],
      actionType: ProcessActionType,
      state: ProcessActionState,
      createdAt: Instant,
      performedAt: Option[Instant],
      failure: Option[String],
      commentId: Option[Long],
      buildInfoProcessingType: Option[ProcessingType]
  )(implicit user: LoggedUser): DB[ProcessActionEntityData] = {
    val actionId         = actionIdOpt.getOrElse(ProcessActionId(UUID.randomUUID()))
    val buildInfoJsonOpt = buildInfoProcessingType.flatMap(buildInfos.forType).map(BuildInfo.writeAsJson)
    val processActionData = ProcessActionEntityData(
      id = actionId,
      processId = processId,
      processVersionId = processVersion,
      user = user.username, // TODO: it should be user.id not name
      createdAt = Timestamp.from(createdAt),
      performedAt = performedAt.map(Timestamp.from),
      actionType = actionType,
      state = state,
      failureMessage = failure,
      commentId = commentId,
      buildInfo = buildInfoJsonOpt
    )
    (processActionsTable += processActionData).map { insertCount =>
      if (insertCount != 1)
        throw new IllegalArgumentException(s"Action with id: $actionId can't be inserted")
      processActionData
    }
  }

  private def updateAction(
      actionId: ProcessActionId,
      state: ProcessActionState,
      performedAt: Option[Instant],
      failure: Option[String],
      commentId: Option[Long]
  ): DB[Boolean] = {
    for {
      updateCount <- processActionsTable
        .filter(_.id === actionId)
        .map(a => (a.performedAt, a.state, a.failureMessage, a.commentId))
        .update((performedAt.map(Timestamp.from), state, failure, commentId))
    } yield updateCount == 1
  }

  // we use "select for update where false" query syntax to lock the table - it is useful if you plan to insert something in a critical section
  def lockActionsTable: DB[Unit] = {
    run(processActionsTable.filter(_ => false).forUpdate.result.map(_ => ()))
  }

  def getInProgressActionTypes(processId: ProcessId): DB[Set[ProcessActionType]] = {
    val query = processActionsTable
      .filter(action => action.processId === processId && action.state === ProcessActionState.InProgress)
      .map(_.actionType)
      .distinct
    run(query.result.map(_.toSet))
  }

  def getInProgressActionTypes(
      allowedActionTypes: Set[ProcessActionType]
  ): DB[Map[ProcessId, Set[ProcessActionType]]] = {
    val query = processActionsTable
      .filter(action =>
        action.state === ProcessActionState.InProgress &&
          action.actionType
            .inSet(allowedActionTypes)
      )
      .map(pa => (pa.processId, pa.actionType))
    run(
      query.result
        .map(_.groupBy { case (process_id, _) => process_id }
          .mapValuesNow(_.map(_._2).toSet))
    )
  }

  def getUserActionsAfter(
      user: LoggedUser,
      possibleActionTypes: Set[ProcessActionType],
      possibleStates: Set[ProcessActionState],
      limit: Instant
  ): DB[Seq[(ProcessActionEntityData, ProcessName)]] = {
    run(
      processActionsTable
        .filter(a =>
          a.user === user.username && a.state.inSet(possibleStates) && a.actionType.inSet(
            possibleActionTypes
          ) && a.performedAt > Timestamp.from(limit)
        )
        .join(processesTable)
        .on((a, p) => p.id === a.processId)
        .map { case (a, p) =>
          (a, p.name)
        }
        .sortBy(_._1.performedAt)
        .result
    )
  }

  def deleteInProgressActions(): DB[Unit] = {
    run(processActionsTable.filter(_.state === ProcessActionState.InProgress).delete.map(_ => ()))
  }

  override def getLastActionPerProcess(
      actionState: Set[ProcessActionState],
      actionTypesOpt: Option[Set[ProcessActionType]]
  ): DB[Map[ProcessId, ProcessAction]] = {
    val query = processActionsTable
      .filter(_.state.inSet(actionState))
      .groupBy(_.processId)
      .map { case (processId, group) => (processId, group.map(_.performedAt).max) }
      .join(processActionsTable)
      .on { case ((processId, maxPerformedAt), action) =>
        action.processId === processId && action.state.inSet(actionState) && action.performedAt === maxPerformedAt
      } // We fetch exactly this one with max deployment
      .map { case ((processId, _), action) => processId -> action }
      .joinLeft(commentsTable)
      .on { case ((_, action), comment) => action.commentId === comment.id }
      .map { case ((processId, action), comment) => processId -> (action, comment) }

    run(
      actionTypesOpt
        .map(actionTypes => query.filter { case (_, (entity, _)) => entity.actionType.inSet(actionTypes) })
        .getOrElse(query)
        .result
        .map(_.toMap.mapValuesNow(toFinishedProcessAction))
    )
  }

  override def getFinishedProcessAction(
      actionId: ProcessActionId
  )(implicit ec: ExecutionContext): DB[Option[ProcessAction]] =
    run(
      processActionsTable
        .filter(a => a.id === actionId && a.state.inSet(ProcessActionState.FinishedStates))
        .joinLeft(commentsTable)
        .on { case (action, comment) => action.commentId === comment.id }
        .result
        .headOption
        .map(_.map(toFinishedProcessAction))
    )

  override def getFinishedProcessActions(processId: ProcessId, actionTypesOpt: Option[Set[ProcessActionType]])(
      implicit ec: ExecutionContext
  ): DB[List[ProcessAction]] = {
    val query = processActionsTable
      .filter(p => p.processId === processId && p.state.inSet(ProcessActionState.FinishedStates))
      .joinLeft(commentsTable)
      .on { case (action, comment) => action.commentId === comment.id }
      .sortBy(_._1.performedAt.desc)
    run(
      actionTypesOpt
        .map(actionTypes => query.filter { case (entity, _) => entity.actionType.inSet(actionTypes) })
        .getOrElse(query)
        .result
        .map(_.toList.map(toFinishedProcessAction))
    )
  }

  private def toFinishedProcessAction(actionData: (ProcessActionEntityData, Option[CommentEntityData])): ProcessAction =
    ProcessAction(
      id = actionData._1.id,
      processId = actionData._1.processId,
      processVersionId = actionData._1.processVersionId
        .getOrElse(throw new AssertionError(s"Process version not available for finished action: ${actionData._1}")),
      createdAt = actionData._1.createdAtTime,
      performedAt = actionData._1.performedAtTime
        .getOrElse(throw new AssertionError(s"PerformedAt not available for finished action: ${actionData._1}")),
      user = actionData._1.user,
      actionType = actionData._1.actionType,
      state = actionData._1.state,
      failureMessage = actionData._1.failureMessage,
      commentId = actionData._2.map(_.id),
      comment = actionData._2.map(_.content),
      buildInfo = actionData._1.buildInfo.flatMap(BuildInfo.parseJson).getOrElse(BuildInfo.empty)
    )

}
