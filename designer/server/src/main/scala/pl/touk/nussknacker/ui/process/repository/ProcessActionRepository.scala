package pl.touk.nussknacker.ui.process.repository

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.deployment.{
  ProcessAction,
  ProcessActionId,
  ProcessActionState,
  ScenarioActionName
}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, ProcessingType, VersionId}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.entity.{CommentEntityData, ProcessActionEntityData}
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.{ImpersonatedUser, LoggedUser, RealLoggedUser}
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

  def getFinishedProcessActions(processId: ProcessId, actionNamesOpt: Option[Set[ScenarioActionName]])(
      implicit ec: ExecutionContext
  ): DB[List[ProcessAction]]

  def getLastFinishedActionsPerProcess: DB[Map[ProcessId, LastFinishedActions]]

}

class DbProcessActionRepository(
    protected val dbRef: DbRef,
    commentRepository: CommentRepository,
    buildInfos: ProcessingTypeDataProvider[Map[String, String], _]
)(implicit ec: ExecutionContext)
    extends DbioRepository
    with NuTables
    with ProcessActionRepository
    with LazyLogging {

  import profile.api._

  def addInProgressAction(
      processId: ProcessId,
      actionName: ScenarioActionName,
      processVersion: Option[VersionId],
      buildInfoProcessingType: Option[ProcessingType]
  )(implicit user: LoggedUser): DB[ProcessActionId] = {
    val now = Instant.now()
    run(
      insertAction(
        None,
        processId,
        processVersion = processVersion,
        actionName = actionName,
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
      actionName: ScenarioActionName,
      processVersion: VersionId,
      performedAt: Instant,
      comment: Option[Comment],
      buildInfoProcessingType: Option[ProcessingType]
  )(implicit user: LoggedUser): DB[Unit] = {
    run(for {
      comment <- saveCommentWhenPassed(processId, processVersion, comment)
      updated <- updateAction(
        actionName,
        actionId,
        ProcessActionState.Finished,
        processId,
        Some(performedAt),
        None,
        comment.map(_.id)
      )
      _ <-
        if (updated) {
          DBIOAction.successful(())
        } else {
          // we have to revert action - in progress action was probably invalidated
          insertAction(
            Some(actionId),
            processId,
            Some(processVersion),
            actionName,
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
      actionName: ScenarioActionName,
      processVersion: Option[VersionId],
      performedAt: Instant,
      failureMessage: String,
      buildInfoProcessingType: Option[ProcessingType]
  )(implicit user: LoggedUser): DB[Unit] = {
    val failureMessageOpt = Option(failureMessage).map(_.take(1022)) // crop to not overflow column size)
    run(for {
      updated <- updateAction(
        actionName,
        actionId,
        ProcessActionState.Failed,
        processId,
        Some(performedAt),
        failureMessageOpt,
        None
      )
      _ <-
        if (updated) {
          DBIOAction.successful(())
        } else {
          // we have to revert action - in progress action was probably invalidated
          insertAction(
            Some(actionId),
            processId,
            processVersion,
            actionName,
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
    addInstantAction(processId, processVersion, ScenarioActionName.Archive, None, None)

  override def markProcessAsUnArchived(processId: ProcessId, processVersion: VersionId)(
      implicit user: LoggedUser
  ): DB[ProcessAction] =
    addInstantAction(processId, processVersion, ScenarioActionName.UnArchive, None, None)

  def addInstantAction(
      processId: ProcessId,
      processVersion: VersionId,
      actionName: ScenarioActionName,
      comment: Option[Comment],
      buildInfoProcessingType: Option[ProcessingType]
  )(implicit user: LoggedUser): DB[ProcessAction] = {
    val now = Instant.now()
    run(for {
      comment <- saveCommentWhenPassed(processId, processVersion, comment)
      result <- insertAction(
        None,
        processId,
        Some(processVersion),
        actionName,
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
      actionName: ScenarioActionName,
      state: ProcessActionState,
      createdAt: Instant,
      performedAt: Option[Instant],
      failure: Option[String],
      commentId: Option[Long],
      buildInfoProcessingType: Option[ProcessingType]
  )(implicit user: LoggedUser): DB[ProcessActionEntityData] = {
    val actionId         = actionIdOpt.getOrElse(ProcessActionId(UUID.randomUUID()))
    val buildInfoJsonOpt = buildInfoProcessingType.flatMap(buildInfos.forProcessingType).map(BuildInfo.writeAsJson)
    val processActionData = ProcessActionEntityData(
      id = actionId,
      processId = processId,
      processVersionId = processVersion,
      user = user.username, // TODO: it should be user.id not name
      impersonatedByIdentity = user.impersonatingUserId,
      impersonatedByUsername = user.impersonatingUserName,
      createdAt = Timestamp.from(createdAt),
      performedAt = performedAt.map(Timestamp.from),
      actionName = actionName,
      state = state,
      failureMessage = failure,
      commentId = commentId,
      buildInfo = buildInfoJsonOpt
    )
    insertProcessActionAndUpdateLatestActionId(processActionData).map { insertCount =>
      if (insertCount != 1)
        throw new IllegalArgumentException(s"Action with id: $actionId can't be inserted")
      processActionData
    }
  }

  private def insertProcessActionAndUpdateLatestActionId(processActionData: ProcessActionEntityData) =
    (for {
      insertCount <- processActionsTable += processActionData

      _ <- updateLatestFinishedActionId(
        processActionData.actionName,
        processActionData.state,
        processActionData.id,
        processActionData.processId
      )
    } yield insertCount).transactionally

  private def updateAction(
      actionName: ScenarioActionName,
      actionId: ProcessActionId,
      state: ProcessActionState,
      processId: ProcessId,
      performedAt: Option[Instant],
      failure: Option[String],
      commentId: Option[Long]
  ): DB[Boolean] =
    (for {
      updateCount <- processActionsTable
        .filter(_.id === actionId)
        .map(a => (a.performedAt, a.state, a.failureMessage, a.commentId))
        .update((performedAt.map(Timestamp.from), state, failure, commentId))

      _ <- updateLatestFinishedActionId(actionName, state, actionId, processId)
    } yield updateCount == 1).transactionally

  private def updateLatestFinishedActionId(
      actionName: ScenarioActionName,
      actionState: ProcessActionState,
      actionId: ProcessActionId,
      processId: ProcessId
  ) =
    if (ProcessActionState.FinishedStates.contains(actionState)) {
      val processQuery = processesTable.filter(_.id === processId)
      val commonUpdate = processQuery.map(_.latestFinishedActionId).update(Some(actionId))

      val specificUpdate = actionName match {
        case ScenarioActionName.Deploy => processQuery.map(_.latestFinishedDeployActionId).update(Some(actionId))
        case ScenarioActionName.Cancel => processQuery.map(_.latestFinishedCancelActionId).update(Some(actionId))
        case _                         => DBIOAction.successful(())
      }

      DBIO.seq(commonUpdate, specificUpdate)
    } else {
      DBIOAction.successful(())
    }

  // we use "select for update where false" query syntax to lock the table - it is useful if you plan to insert something in a critical section
  def lockActionsTable: DB[Unit] = {
    run(processActionsTable.filter(_ => false).forUpdate.result.map(_ => ()))
  }

  def getInProgressActionNames(processId: ProcessId): DB[Set[ScenarioActionName]] = {
    val query = processActionsTable
      .filter(action => action.processId === processId && action.state === ProcessActionState.InProgress)
      .map(_.actionName)
      .distinct
    run(query.result.map(_.toSet))
  }

  def getInProgressActionNames(
      allowedActionNames: Set[ScenarioActionName]
  ): DB[Map[ProcessId, Set[ScenarioActionName]]] = {
    val query = processActionsTable
      .filter(action =>
        action.state === ProcessActionState.InProgress &&
          action.actionName
            .inSet(allowedActionNames)
      )
      .map(pa => (pa.processId, pa.actionName))
    run(
      query.result
        .map(_.groupBy { case (process_id, _) => process_id }
          .mapValuesNow(_.map(_._2).toSet))
    )
  }

  def getUserActionsAfter(
      user: LoggedUser,
      possibleActionNames: Set[ScenarioActionName],
      possibleStates: Set[ProcessActionState],
      limit: Instant
  ): DB[Seq[(ProcessActionEntityData, ProcessName)]] = {
    run(
      processActionsTable
        .filter(a =>
          a.user === user.username && a.state.inSet(possibleStates) && a.actionName.inSet(
            possibleActionNames
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

  override def getLastFinishedActionsPerProcess: DB[Map[ProcessId, LastFinishedActions]] = {
    val query = processActionsTable
      .join(processesTable)
      .on { case (action, process) =>
        action.processId === process.id && action.state.inSet(ProcessActionState.FinishedStates) &&
        (action.id === process.latestFinishedActionId || action.id === process.latestFinishedDeployActionId || action.id === process.latestFinishedCancelActionId)
      }
      .map { case (action, process) => process.id -> action }
      .joinLeft(commentsTable)
      .on { case ((_, action), comment) => action.commentId === comment.id }
      .map { case ((processId, action), comment) => processId -> (action, comment) }

    run(query.result.map(_.foldLeft(Map.empty[ProcessId, Seq[ProcessAction]]) {
      case (map, (processId, (action, comment))) =>
        val finishedAction = toFinishedProcessAction((action, comment))

        map.get(processId) match {
          case Some(latestActions) =>
            map + (processId -> (latestActions :+ finishedAction))
          case None =>
            map + (processId -> Seq(finishedAction))
        }
    }.mapValuesNow {
      LastFinishedActions
    }))
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

  override def getFinishedProcessActions(processId: ProcessId, actionNamesOpt: Option[Set[ScenarioActionName]])(
      implicit ec: ExecutionContext
  ): DB[List[ProcessAction]] = {
    val query = processActionsTable
      .filter(p => p.processId === processId && p.state.inSet(ProcessActionState.FinishedStates))
      .joinLeft(commentsTable)
      .on { case (action, comment) => action.commentId === comment.id }
      .sortBy(_._1.performedAt.desc)
    run(
      actionNamesOpt
        .map(actionNames => query.filter { case (entity, _) => entity.actionName.inSet(actionNames) })
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
      actionName = actionData._1.actionName,
      state = actionData._1.state,
      failureMessage = actionData._1.failureMessage,
      commentId = actionData._2.map(_.id),
      comment = actionData._2.map(_.content),
      buildInfo = actionData._1.buildInfo.flatMap(BuildInfo.parseJson).getOrElse(BuildInfo.empty)
    )

  private def saveCommentWhenPassed(
      scenarioId: ProcessId,
      scenarioGraphVersionId: => VersionId,
      commentOpt: Option[Comment]
  )(
      implicit user: LoggedUser
  ): DB[Option[CommentEntityData]] =
    commentOpt
      .map(commentRepository.saveComment(scenarioId, scenarioGraphVersionId, user, _))
      .sequence

}
