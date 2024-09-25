package pl.touk.nussknacker.ui.process.repository

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.deployment.ProcessActionState.ProcessActionState
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, ProcessingType, VersionId}
import pl.touk.nussknacker.engine.management.periodic.InstantBatchCustomAction
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.entity.{AdditionalProperties, ScenarioActivityEntityData, ScenarioActivityType}
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.engine.api.Comment
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.dbio.DBIOAction

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext

//TODO: Add missing methods: markProcessAsDeployed and markProcessAsCancelled
trait ScenarioActionRepository {

  def markProcessAsArchived(
      processId: ProcessId,
      processVersion: VersionId
  )(implicit user: LoggedUser): DB[_]

  def markProcessAsUnArchived(
      processId: ProcessId,
      processVersion: VersionId
  )(implicit user: LoggedUser): DB[_]

  def getFinishedProcessAction(
      actionId: ProcessActionId
  ): DB[Option[ProcessAction]]

  def getFinishedProcessActions(
      processId: ProcessId,
      actionNamesOpt: Option[Set[ScenarioActionName]]
  ): DB[List[ProcessAction]]

  def getLastActionPerProcess(
      actionState: Set[ProcessActionState],
      actionNamesOpt: Option[Set[ScenarioActionName]]
  ): DB[Map[ProcessId, ProcessAction]]

  def getUserActionsAfter(
      user: LoggedUser,
      possibleActionNames: Set[ScenarioActionName],
      possibleStates: Set[ProcessActionState],
      limit: Instant
  ): DB[List[(ProcessAction, ProcessName)]]

}

class DbScenarioActionRepository(
    protected val dbRef: DbRef,
    buildInfos: ProcessingTypeDataProvider[Map[String, String], _]
)(implicit ec: ExecutionContext)
    extends DbioRepository
    with NuTables
    with ScenarioActionRepository
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
        comment = None,
        buildInfoProcessingType = buildInfoProcessingType
      ).map(_.activityId.value).map(ProcessActionId.apply)
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
      updated <- updateAction(actionId, ProcessActionState.Finished, Some(performedAt), None, comment)
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
            comment,
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
      scenarioActivityTable
        .filter(a => a.activityId === activityId(actionId) && a.state === ProcessActionState.Finished)
        .map(_.state)
        .update(Some(ProcessActionState.ExecutionFinished))
        .map(_ == 1)
    )
  }

  def removeAction(actionId: ProcessActionId): DB[Unit] = {
    run(scenarioActivityTable.filter(a => a.activityId === activityId(actionId)).delete.map(_ => ()))
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
    run(
      insertAction(
        None,
        processId,
        Some(processVersion),
        actionName,
        ProcessActionState.Finished,
        now,
        Some(now),
        None,
        comment,
        buildInfoProcessingType
      ).map(a => toFinishedProcessAction(a))
    )
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
      comment: Option[Comment],
      buildInfoProcessingType: Option[ProcessingType]
  )(implicit user: LoggedUser): DB[ScenarioActivityEntityData] = {
    val actionId         = actionIdOpt.getOrElse(ProcessActionId(UUID.randomUUID()))
    val buildInfoJsonOpt = buildInfoProcessingType.flatMap(buildInfos.forProcessingType).map(BuildInfo.writeAsJson)

    val activityType = actionName match {
      case ScenarioActionName.Deploy =>
        ScenarioActivityType.ScenarioDeployed
      case ScenarioActionName.Cancel =>
        ScenarioActivityType.ScenarioCanceled
      case ScenarioActionName.Archive =>
        ScenarioActivityType.ScenarioArchived
      case ScenarioActionName.UnArchive =>
        ScenarioActivityType.ScenarioUnarchived
      case ScenarioActionName.Pause =>
        ScenarioActivityType.ScenarioPaused
      case ScenarioActionName.Rename =>
        ScenarioActivityType.ScenarioNameChanged
      case InstantBatchCustomAction.name =>
        ScenarioActivityType.PerformedSingleExecution
      case otherCustomName =>
        ScenarioActivityType.CustomAction(otherCustomName.value)
    }
    val entity = ScenarioActivityEntityData(
      id = -1,
      activityType = activityType,
      scenarioId = processId,
      activityId = ScenarioActivityId(actionIdOpt.map(_.value).getOrElse(UUID.randomUUID())),
      userId = Some(user.id),
      userName = user.username,
      impersonatedByUserId = user.impersonatingUserId,
      impersonatedByUserName = user.impersonatingUserName,
      lastModifiedByUserName = Some(user.username),
      lastModifiedAt = Some(Timestamp.from(createdAt)),
      createdAt = Timestamp.from(createdAt),
      scenarioVersion = processVersion.map(_.value).map(ScenarioVersionId.apply),
      comment = comment.map(_.content),
      attachmentId = None,
      finishedAt = performedAt.map(Timestamp.from),
      state = Some(state),
      errorMessage = failure,
      buildInfo = buildInfoJsonOpt,
      additionalProperties = AdditionalProperties(Map.empty)
    )
    (scenarioActivityTable += entity).map { insertCount =>
      if (insertCount != 1)
        throw new IllegalArgumentException(s"Action with id: $actionId can't be inserted")
      entity
    }
  }

  private def updateAction(
      actionId: ProcessActionId,
      state: ProcessActionState,
      performedAt: Option[Instant],
      failure: Option[String],
      comment: Option[Comment],
  ): DB[Boolean] = {
    for {
      updateCount <- scenarioActivityTable
        .filter(_.activityId === activityId(actionId))
        .map(a => (a.performedAt, a.state, a.errorMessage, a.comment))
        .update(
          (performedAt.map(Timestamp.from), Some(state), failure, comment.map(_.content))
        )
    } yield updateCount == 1
  }

  // we use "select for update where false" query syntax to lock the table - it is useful if you plan to insert something in a critical section
  def lockActionsTable: DB[Unit] = {
    run(scenarioActivityTable.filter(_ => false).forUpdate.result.map(_ => ()))
  }

  def getInProgressActionNames(processId: ProcessId): DB[Set[ScenarioActionName]] = {
    val query = scenarioActivityTable
      .filter(action => action.scenarioId === processId && action.state === ProcessActionState.InProgress)
      .map(_.activityType)
      .distinct
    run(query.result.map(_.toSet.flatMap(actionName)))
  }

  def getInProgressActionNames(
      allowedActionNames: Set[ScenarioActionName]
  ): DB[Map[ProcessId, Set[ScenarioActionName]]] = {
    val query = scenarioActivityTable
      .filter(action =>
        action.state === ProcessActionState.InProgress &&
          action.activityType
            .inSet(activityTypes(allowedActionNames))
      )
      .map(pa => (pa.scenarioId, pa.activityType))
    run(
      query.result
        .map(_.groupBy { case (process_id, _) => ProcessId(process_id.value) }
          .mapValuesNow(_.map(_._2).toSet.flatMap(actionName)))
    )
  }

  def getUserActionsAfter(
      loggedUser: LoggedUser,
      possibleActionNames: Set[ScenarioActionName],
      possibleStates: Set[ProcessActionState],
      limit: Instant
  ): DB[List[(ProcessAction, ProcessName)]] = {
    run(
      scenarioActivityTable
        .filter(a =>
          a.userId === loggedUser.id && a.state.inSet(possibleStates) && a.activityType.inSet(
            activityTypes(possibleActionNames)
          ) && a.performedAt > Timestamp.from(limit)
        )
        .join(processesTable)
        .on((a, p) => p.id === a.scenarioId)
        .map { case (a, p) =>
          (a, p.name)
        }
        .sortBy(_._1.performedAt)
        .result
        .map(_.map { case (data, name) =>
          (toFinishedProcessAction(data), name)
        }.toList)
    )
  }

  def deleteInProgressActions(): DB[Unit] = {
    run(scenarioActivityTable.filter(_.state === ProcessActionState.InProgress).delete.map(_ => ()))
  }

  override def getLastActionPerProcess(
      actionState: Set[ProcessActionState],
      actionNamesOpt: Option[Set[ScenarioActionName]]
  ): DB[Map[ProcessId, ProcessAction]] = {
    val activityTypes = actionNamesOpt.getOrElse(Set.empty).map(activityType).toList

    val queryWithActionNamesFilter = NonEmptyList.fromList(activityTypes) match {
      case Some(activityTypes) =>
        scenarioActivityTable.filter { action => action.activityType.inSet(activityTypes.toList) }
      case None =>
        scenarioActivityTable
    }

    val finalQuery = queryWithActionNamesFilter
      .filter(_.state.inSet(actionState))
      .groupBy(_.scenarioId)
      .map { case (processId, group) => (processId, group.map(_.performedAt).max) }
      .join(scenarioActivityTable)
      .on { case ((scenarioId, maxPerformedAt), action) =>
        action.scenarioId === scenarioId && action.state.inSet(actionState) && action.performedAt === maxPerformedAt
      } // We fetch exactly this one with max deployment
      .map { case ((scenarioId, _), activity) => scenarioId -> activity }

    run(
      finalQuery.result.map(_.map { case (scenarioId, action) =>
        (ProcessId(scenarioId.value), toFinishedProcessAction(action))
      }.toMap)
    )
  }

  override def getFinishedProcessAction(
      actionId: ProcessActionId
  ): DB[Option[ProcessAction]] =
    run(
      scenarioActivityTable
        .filter(a =>
          a.activityId === ScenarioActivityId(actionId.value) && a.state.inSet(
            ProcessActionState.FinishedStates
          )
        )
        .result
        .headOption
        .map(_.map(toFinishedProcessAction))
    )

  override def getFinishedProcessActions(
      processId: ProcessId,
      actionNamesOpt: Option[Set[ScenarioActionName]]
  ): DB[List[ProcessAction]] = {
    val query = scenarioActivityTable
      .filter(p => p.scenarioId === processId && p.state.inSet(ProcessActionState.FinishedStates))
      .sortBy(_.performedAt.desc)
    run(
      actionNamesOpt
        .map(actionNames => query.filter { entity => entity.activityType.inSet(activityTypes(actionNames)) })
        .getOrElse(query)
        .result
        .map(_.toList.map(toFinishedProcessAction))
    )
  }

  private def toFinishedProcessAction(activityEntity: ScenarioActivityEntityData): ProcessAction = {
    ProcessAction(
      id = ProcessActionId(activityEntity.activityId.value),
      processId = ProcessId(activityEntity.scenarioId.value),
      processVersionId = activityEntity.scenarioVersion
        .map(_.value)
        .map(VersionId.apply)
        .getOrElse(throw new AssertionError(s"Process version not available for finished action: $activityEntity")),
      createdAt = activityEntity.createdAt.toInstant,
      performedAt = activityEntity.finishedAt
        .map(_.toInstant)
        .getOrElse(throw new AssertionError(s"PerformedAt not available for finished action: $activityEntity")),
      user = activityEntity.userName.value,
      actionName = actionName(activityEntity.activityType)
        .getOrElse(throw new AssertionError(s"ActionName not available for finished action: $activityEntity")),
      state = activityEntity.state
        .getOrElse(throw new AssertionError(s"State not available for finished action: $activityEntity")),
      failureMessage = activityEntity.errorMessage,
      commentId = activityEntity.comment.map(_ => activityEntity.id),
      comment = activityEntity.comment.map(_.value),
      buildInfo = activityEntity.buildInfo.flatMap(BuildInfo.parseJson).getOrElse(BuildInfo.empty)
    )
  }

  private def activityId(actionId: ProcessActionId) =
    ScenarioActivityId(actionId.value)

  private def actionName(activityType: ScenarioActivityType): Option[ScenarioActionName] = {
    activityType match {
      case ScenarioActivityType.ScenarioCreated =>
        None
      case ScenarioActivityType.ScenarioArchived =>
        Some(ScenarioActionName.Archive)
      case ScenarioActivityType.ScenarioUnarchived =>
        Some(ScenarioActionName.UnArchive)
      case ScenarioActivityType.ScenarioDeployed =>
        Some(ScenarioActionName.Deploy)
      case ScenarioActivityType.ScenarioPaused =>
        Some(ScenarioActionName.Pause)
      case ScenarioActivityType.ScenarioCanceled =>
        Some(ScenarioActionName.Cancel)
      case ScenarioActivityType.ScenarioModified =>
        None
      case ScenarioActivityType.ScenarioNameChanged =>
        Some(ScenarioActionName.Rename)
      case ScenarioActivityType.CommentAdded =>
        None
      case ScenarioActivityType.AttachmentAdded =>
        None
      case ScenarioActivityType.ChangedProcessingMode =>
        None
      case ScenarioActivityType.IncomingMigration =>
        None
      case ScenarioActivityType.OutgoingMigration =>
        None
      case ScenarioActivityType.PerformedSingleExecution =>
        None
      case ScenarioActivityType.PerformedScheduledExecution =>
        None
      case ScenarioActivityType.AutomaticUpdate =>
        None
      case ScenarioActivityType.CustomAction(name) =>
        Some(ScenarioActionName(name))
    }
  }

  private def activityTypes(actionNames: Set[ScenarioActionName]): Set[ScenarioActivityType] = {
    actionNames.map(activityType)
  }

  private def activityType(actionName: ScenarioActionName): ScenarioActivityType = {
    actionName match {
      case ScenarioActionName.Deploy =>
        ScenarioActivityType.ScenarioDeployed
      case ScenarioActionName.Cancel =>
        ScenarioActivityType.ScenarioCanceled
      case ScenarioActionName.Archive =>
        ScenarioActivityType.ScenarioArchived
      case ScenarioActionName.UnArchive =>
        ScenarioActivityType.ScenarioUnarchived
      case ScenarioActionName.Pause =>
        ScenarioActivityType.ScenarioPaused
      case ScenarioActionName.Rename =>
        ScenarioActivityType.ScenarioNameChanged
      case otherCustomAction =>
        ScenarioActivityType.CustomAction(otherCustomAction.value)
    }
  }

}
