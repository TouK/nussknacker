package pl.touk.nussknacker.ui.process

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.process.ScenarioAttachmentService.AttachmentToAdd
import pl.touk.nussknacker.ui.security.api.LoggedUser

object ScenarioActivityAuditLog extends LazyLogging {

  def onCreateScenarioActivity(
      scenarioActivity: ScenarioActivity
  ): Unit =
    logger.info(
      withPrefix(scenarioActivity.scenarioId, scenarioActivity.scenarioVersionId, scenarioActivity.user.name.value)(
        s"New activity: ${printScenarioActivity(scenarioActivity)}"
      )
    )

  private def printScenarioActivity(scenarioActivity: ScenarioActivity) = scenarioActivity match {
    case ScenarioActivity.ScenarioDeployed(_, _, _, _, _, comment, result) =>
      s"ScenarioDeployed(comment=${printComment(comment)},result=${printResult(result)})"
    case ScenarioActivity.ScenarioPaused(_, _, _, _, _, comment, result) =>
      s"ScenarioPaused(comment=${printComment(comment)},result=${printResult(result)})"
    case ScenarioActivity.ScenarioCanceled(_, _, _, _, _, comment, result) =>
      s"ScenarioCanceled(comment=${printComment(comment)},result=${printResult(result)})"
    case ScenarioActivity.CustomAction(_, _, _, _, _, actionName, comment, result) =>
      s"CustomAction(action=$actionName,comment=${printComment(comment)},result=${printResult(result)})"
    case ScenarioActivity.PerformedSingleExecution(_, _, _, _, _, comment, result) =>
      s"PerformedSingleExecution(comment=${printComment(comment)},result=${printResult(result)})"
    case ScenarioActivity.PerformedScheduledExecution(_, _, _, _, _, status, _, scheduleName, _, _, _) =>
      s"PerformedScheduledExecution(scheduleName=$scheduleName,scheduledExecutionStatus=${status.entryName})"
    case ScenarioActivity.ScenarioCreated(_, _, _, _, _) =>
      "ScenarioCreated"
    case ScenarioActivity.ScenarioArchived(_, _, _, _, _) =>
      "ScenarioArchived"
    case ScenarioActivity.ScenarioUnarchived(_, _, _, _, _) =>
      "ScenarioUnarchived"
    case ScenarioActivity.ScenarioModified(_, _, _, _, _, _, comment) =>
      s"ScenarioModified(comment=${printComment(comment)})"
    case ScenarioActivity.ScenarioNameChanged(_, _, _, _, _, oldName, newName) =>
      s"ScenarioNameChanged(oldName=$oldName,newName=$newName)"
    case ScenarioActivity.CommentAdded(_, _, _, _, _, comment) =>
      s"CommentAdded(comment=${printComment(comment)})"
    case ScenarioActivity.AttachmentAdded(_, _, _, _, _, attachment) =>
      s"AttachmentAdded(fileName=${printAttachment(attachment)})"
    case ScenarioActivity.ChangedProcessingMode(_, _, _, _, _, from, to) =>
      s"ChangedProcessingMode(from=$from,to=$to)"
    case ScenarioActivity.IncomingMigration(_, _, _, _, _, sourceEnvironment, sourceUser, sourceVersionId, _) =>
      s"IncomingMigration(sourceEnvironment=${sourceEnvironment.name},sourceUser=${sourceUser.value},sourceVersionId=${sourceVersionId
          .map(_.value.toString)
          .getOrElse("[none]")})"
    case ScenarioActivity.OutgoingMigration(_, _, _, _, _, destinationEnvironment) =>
      s"OutgoingMigration(destinationEnvironment=${destinationEnvironment.name})"
    case ScenarioActivity.AutomaticUpdate(_, _, _, _, _, changes) =>
      s"AutomaticUpdate(changes=$changes)"
  }

  private def printAttachment(attachment: ScenarioAttachment) = attachment match {
    case ScenarioAttachment.Available(_, attachmentFilename, _, _) => s"Available(${attachmentFilename.value})"
    case ScenarioAttachment.Deleted(attachmentFilename, _, _)      => s"Deleted(${attachmentFilename.value})"
  }

  private def printComment(comment: ScenarioComment) = comment match {
    case ScenarioComment.Available(comment, _, _) => comment
    case ScenarioComment.Deleted(_, _)            => "[none]"
  }

  private def printResult(result: DeploymentResult) = result match {
    case DeploymentResult.Success(_)               => "Success"
    case DeploymentResult.Failure(_, errorMessage) => s"Failure($errorMessage)"
  }

  def onAddComment(
      processId: ProcessId,
      versionId: Option[VersionId],
      user: LoggedUser,
      scenarioActivityId: ScenarioActivityId,
      comment: String,
  ): Unit =
    logger.info(
      withPrefix(ScenarioId(processId.value), versionId.map(ScenarioVersionId.from), user.username)(
        s"[commentId=${scenarioActivityId.value.toString}] Comment added: [$comment]"
      )
    )

  def onEditComment(
      processId: ProcessId,
      user: LoggedUser,
      scenarioActivityId: ScenarioActivityId,
      comment: String
  ): Unit =
    logger.info(
      withPrefix(ScenarioId(processId.value), None, user.username)(
        s"[commentId=${scenarioActivityId.value.toString}] Comment edited, new value: [$comment]"
      )
    )

  def onDeleteComment(
      processId: ProcessId,
      rowId: Long,
      user: LoggedUser,
  ): Unit =
    logger.info(
      withPrefix(ScenarioId(processId.value), None, user.username)(
        s"Comment with rowId=$rowId deleted"
      )
    )

  def onDeleteComment(
      processId: ProcessId,
      activityId: ScenarioActivityId,
      user: LoggedUser,
  ): Unit =
    logger.info(
      withPrefix(ScenarioId(processId.value), None, user.username)(
        s"Comment for activityId=${activityId.value} deleted"
      )
    )

  def onAddAttachment(
      attachmentToAdd: AttachmentToAdd,
      user: LoggedUser,
  ): Unit =
    logger.info(
      withPrefix(
        ScenarioId(attachmentToAdd.scenarioId.value),
        Some(ScenarioVersionId.from(attachmentToAdd.scenarioVersionId)),
        user.username
      )(
        s"Attachment added: [${attachmentToAdd.fileName}]"
      )
    )

  def onScenarioImmediateAction(
      processActionId: ProcessActionId,
      processId: ProcessId,
      actionName: ScenarioActionName,
      processVersion: Option[VersionId],
      user: LoggedUser
  ): Unit =
    logger.info(
      withPrefix(ScenarioId(processId.value), processVersion.map(ScenarioVersionId.from), user.username)(
        s"Immediate scenario action [actionName=${actionName.value},actionId=${processActionId.value}]"
      )
    )

  def onScenarioActionStarted(
      processActionId: ProcessActionId,
      processId: ProcessId,
      actionName: ScenarioActionName,
      processVersion: Option[VersionId],
      user: LoggedUser
  ): Unit =
    logger.info(
      withPrefix(ScenarioId(processId.value), processVersion.map(ScenarioVersionId.from), user.username)(
        s"Scenario action [actionName=${actionName.value},actionId=${processActionId.value}] started"
      )
    )

  def onScenarioActionFinishedWithSuccess(
      processActionId: ProcessActionId,
      processId: ProcessId,
      actionName: ScenarioActionName,
      processVersion: Option[VersionId],
      comment: Option[String],
      user: LoggedUser
  ): Unit = {
    val commentValue = comment match {
      case Some(content) => s"comment [$content]"
      case None          => "without comment"
    }
    logger.info(
      withPrefix(ScenarioId(processId.value), processVersion.map(ScenarioVersionId.from), user.username)(
        s"Scenario action [actionName=${actionName.value},actionId=${processActionId.value}] finished with success and $commentValue "
      )
    )
  }

  def onScenarioActionFinishedWithFailure(
      processActionId: ProcessActionId,
      processId: ProcessId,
      actionName: ScenarioActionName,
      processVersion: Option[VersionId],
      comment: Option[String],
      failureMessage: String,
      user: LoggedUser
  ): Unit = {
    val commentValue = comment match {
      case Some(content) => s"with comment [$content]"
      case None          => "without comment"
    }
    logger.info(
      withPrefix(ScenarioId(processId.value), processVersion.map(ScenarioVersionId.from), user.username)(
        s"Scenario action [actionName=${actionName.value},actionId=${processActionId.value}] finished with failure [$failureMessage] $commentValue"
      )
    )
  }

  def onScenarioActionRemoved(
      processActionId: ProcessActionId,
      processId: ProcessId,
      processVersion: Option[VersionId],
      user: LoggedUser
  ): Unit = {
    logger.info(
      withPrefix(ScenarioId(processId.value), processVersion.map(ScenarioVersionId.from), user.username)(
        s"Scenario action [actionId=${processActionId.value}] removed"
      )
    )
  }

  private def withPrefix(scenarioId: ScenarioId, scenarioVersionId: Option[ScenarioVersionId], username: String)(
      log: String
  ) = {
    val scenarioIdValue        = scenarioId.value
    val scenarioVersionIdValue = scenarioVersionId.map(_.value.toString).getOrElse("none")
    s"[SCENARIO_AUDIT][scenarioId=$scenarioIdValue][version=$scenarioVersionIdValue][user=$username] $log"
  }

}
