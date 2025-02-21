package pl.touk.nussknacker.ui.process.repository

import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.Comment
import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.ui.process.ScenarioActivityAuditLog
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.FunctorUtils.Ops

import java.time.Instant
import scala.concurrent.ExecutionContext

class ScenarioActionRepositoryAuditLogDecorator(underlying: ScenarioActionRepository)(
    implicit executionContext: ExecutionContext
) extends ScenarioActionRepository {

  override def addInstantAction(
      processId: ProcessId,
      processVersion: VersionId,
      actionName: ScenarioActionName,
      comment: Option[Comment],
  )(implicit user: LoggedUser): DB[ProcessAction] =
    underlying
      .addInstantAction(processId, processVersion, actionName, comment)
      .onSuccessRunAsync(processAction =>
        ScenarioActivityAuditLog.onScenarioImmediateAction(
          processAction.id,
          processId,
          actionName,
          Some(processVersion),
          user
        )
      )

  override def addInProgressAction(
      processId: ProcessId,
      actionName: ScenarioActionName,
      processVersion: Option[VersionId],
  )(implicit user: LoggedUser): DB[ProcessActionId] =
    underlying
      .addInProgressAction(processId, actionName, processVersion)
      .onSuccessRunAsync(processActionId =>
        ScenarioActivityAuditLog.onScenarioActionStarted(processActionId, processId, actionName, processVersion, user)
      )

  override def markActionAsFinished(
      actionId: ProcessActionId,
      processId: ProcessId,
      actionName: ScenarioActionName,
      processVersion: VersionId,
      performedAt: Instant,
      comment: Option[Comment],
  )(implicit user: LoggedUser): DB[Unit] =
    underlying
      .markActionAsFinished(
        actionId,
        processId,
        actionName,
        processVersion,
        performedAt,
        comment,
      )
      .onSuccessRunAsync(_ =>
        ScenarioActivityAuditLog
          .onScenarioActionFinishedWithSuccess(
            actionId,
            processId,
            actionName,
            Some(processVersion),
            comment.map(_.content),
            user
          )
      )

  override def markActionAsFailed(
      actionId: ProcessActionId,
      processId: ProcessId,
      actionName: ScenarioActionName,
      processVersion: Option[VersionId],
      performedAt: Instant,
      comment: Option[Comment],
      failureMessage: String,
  )(implicit user: LoggedUser): DB[Unit] =
    underlying
      .markActionAsFailed(
        actionId,
        processId,
        actionName,
        processVersion,
        performedAt,
        comment,
        failureMessage,
      )
      .onSuccessRunAsync(_ =>
        ScenarioActivityAuditLog
          .onScenarioActionFinishedWithFailure(
            actionId,
            processId,
            actionName,
            processVersion,
            comment.map(_.content),
            failureMessage,
            user
          )
      )

  override def removeAction(actionId: ProcessActionId, processId: ProcessId, processVersion: Option[VersionId])(
      implicit user: LoggedUser
  ): DB[Unit] =
    underlying
      .removeAction(actionId, processId, processVersion)
      .onSuccessRunAsync(_ =>
        ScenarioActivityAuditLog
          .onScenarioActionRemoved(
            actionId,
            processId,
            processVersion,
            user,
          )
      )

  override def deleteInProgressActions(): DB[Unit] =
    underlying.deleteInProgressActions()

  override def markFinishedActionAsExecutionFinished(
      actionId: ProcessActionId
  ): DB[Boolean] =
    underlying.markFinishedActionAsExecutionFinished(actionId)

  override def getInProgressActionNames(processId: ProcessId): DB[Set[ScenarioActionName]] =
    underlying.getInProgressActionNames(processId)

  override def getInProgressActionNames(
      allowedActionNames: Set[ScenarioActionName]
  ): DB[Map[ProcessId, Set[ScenarioActionName]]] =
    underlying.getInProgressActionNames(allowedActionNames)

  override def getFinishedProcessAction(
      actionId: ProcessActionId
  ): DB[Option[ProcessAction]] =
    underlying.getFinishedProcessAction(actionId)

  override def getFinishedProcessActions(
      processId: ProcessId,
      actionNamesOpt: Option[Set[ScenarioActionName]]
  ): DB[List[ProcessAction]] =
    underlying.getFinishedProcessActions(processId, actionNamesOpt)

  override def getLastActionPerProcess(
      actionState: Set[ProcessActionState],
      actionNamesOpt: Option[Set[ScenarioActionName]]
  ): DB[Map[ProcessId, ProcessAction]] =
    underlying.getLastActionPerProcess(actionState, actionNamesOpt)

  override def getUserActionsAfter(
      user: LoggedUser,
      possibleActionNames: Set[ScenarioActionName],
      possibleStates: Set[ProcessActionState],
      limit: Instant
  ): DB[List[(ProcessAction, ProcessName)]] =
    underlying.getUserActionsAfter(user, possibleActionNames, possibleStates, limit)

  override def withLockedTable[T](dbioAction: DB[T]): DB[T] =
    underlying.withLockedTable(dbioAction)

}
