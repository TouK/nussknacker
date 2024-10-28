package pl.touk.nussknacker.ui.process

import cats.effect.IO
import com.typesafe.scalalogging.Logger
import org.slf4j.{LoggerFactory, MDC}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.process.ScenarioAttachmentService.AttachmentToAdd
import pl.touk.nussknacker.ui.security.api.LoggedUser

object ScenarioActivityAuditLog {

  private val logger = Logger(LoggerFactory.getLogger(s"scenario-activity-audit"))

  def onCreateScenarioActivity(
      scenarioActivity: ScenarioActivity
  ): IO[Unit] =
    logWithContext(scenarioActivity.scenarioId, scenarioActivity.scenarioVersionId, scenarioActivity.user.name.value)(
      s"New activity: ${stringify(scenarioActivity)}"
    )

  def onEditComment(
      processId: ProcessId,
      user: LoggedUser,
      scenarioActivityId: ScenarioActivityId,
      comment: String
  ): IO[Unit] = {
    logWithContext(ScenarioId(processId.value), None, user.username)(
      s"[commentId=${scenarioActivityId.value.toString}] Comment edited, new value: [$comment]"
    )
  }

  def onDeleteComment(
      processId: ProcessId,
      rowId: Long,
      user: LoggedUser,
  ): IO[Unit] =
    logWithContext(ScenarioId(processId.value), None, user.username)(
      s"Comment with rowId=$rowId deleted"
    )

  def onDeleteComment(
      processId: ProcessId,
      activityId: ScenarioActivityId,
      user: LoggedUser,
  ): IO[Unit] =
    logWithContext(ScenarioId(processId.value), None, user.username)(
      s"Comment for activityId=${activityId.value} deleted"
    )

  def onAddAttachment(
      attachmentToAdd: AttachmentToAdd,
      user: LoggedUser,
  ): IO[Unit] =
    logWithContext(
      ScenarioId(attachmentToAdd.scenarioId.value),
      Some(ScenarioVersionId.from(attachmentToAdd.scenarioVersionId)),
      user.username
    )(s"Attachment added: [${attachmentToAdd.fileName}]")

  def onDeleteAttachment(
      scenarioId: ProcessId,
      attachmentId: Long,
      user: LoggedUser,
  ): IO[Unit] =
    logWithContext(
      ScenarioId(scenarioId.value),
      None,
      user.username
    )(s"Attachment deleted: [attachmentId=$attachmentId]")

  def onScenarioImmediateAction(
      processActionId: ProcessActionId,
      processId: ProcessId,
      actionName: ScenarioActionName,
      processVersion: Option[VersionId],
      user: LoggedUser
  ): IO[Unit] =
    logWithContext(ScenarioId(processId.value), processVersion.map(ScenarioVersionId.from), user.username)(
      s"Immediate scenario action [actionName=${actionName.value},actionId=${processActionId.value}]"
    )

  def onScenarioActionStarted(
      processActionId: ProcessActionId,
      processId: ProcessId,
      actionName: ScenarioActionName,
      processVersion: Option[VersionId],
      user: LoggedUser
  ): IO[Unit] =
    logWithContext(ScenarioId(processId.value), processVersion.map(ScenarioVersionId.from), user.username)(
      s"Scenario action [actionName=${actionName.value},actionId=${processActionId.value}] started"
    )

  def onScenarioActionFinishedWithSuccess(
      processActionId: ProcessActionId,
      processId: ProcessId,
      actionName: ScenarioActionName,
      processVersion: Option[VersionId],
      comment: Option[String],
      user: LoggedUser
  ): IO[Unit] = {
    val commentValue = comment match {
      case Some(content) => s"comment [$content]"
      case None          => "without comment"
    }
    logWithContext(ScenarioId(processId.value), processVersion.map(ScenarioVersionId.from), user.username)(
      s"Scenario action [actionName=${actionName.value},actionId=${processActionId.value}] finished with success and $commentValue "
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
  ): IO[Unit] = {
    val commentValue = comment match {
      case Some(content) => s"with comment [$content]"
      case None          => "without comment"
    }
    logWithContext(ScenarioId(processId.value), processVersion.map(ScenarioVersionId.from), user.username)(
      s"Scenario action [actionName=${actionName.value},actionId=${processActionId.value}] finished with failure [$failureMessage] $commentValue"
    )
  }

  def onScenarioActionRemoved(
      processActionId: ProcessActionId,
      processId: ProcessId,
      processVersion: Option[VersionId],
      user: LoggedUser
  ): IO[Unit] = {
    logWithContext(ScenarioId(processId.value), processVersion.map(ScenarioVersionId.from), user.username)(
      s"Scenario action [actionId=${processActionId.value}] removed"
    )
  }

  private def stringify(scenarioActivity: ScenarioActivity): String = scenarioActivity match {
    case ScenarioActivity.ScenarioDeployed(_, _, _, _, _, comment, result) =>
      s"ScenarioDeployed(comment=${stringify(comment)},result=${stringify(result)})"
    case ScenarioActivity.ScenarioPaused(_, _, _, _, _, comment, result) =>
      s"ScenarioPaused(comment=${stringify(comment)},result=${stringify(result)})"
    case ScenarioActivity.ScenarioCanceled(_, _, _, _, _, comment, result) =>
      s"ScenarioCanceled(comment=${stringify(comment)},result=${stringify(result)})"
    case ScenarioActivity.CustomAction(_, _, _, _, _, actionName, comment, result) =>
      s"CustomAction(action=$actionName,comment=${stringify(comment)},result=${stringify(result)})"
    case ScenarioActivity.PerformedSingleExecution(_, _, _, _, _, comment, result) =>
      s"PerformedSingleExecution(comment=${stringify(comment)},result=${stringify(result)})"
    case ScenarioActivity.PerformedScheduledExecution(_, _, _, _, _, status, _, scheduleName, _, _, _) =>
      s"PerformedScheduledExecution(scheduleName=$scheduleName,scheduledExecutionStatus=${status.entryName})"
    case ScenarioActivity.ScenarioCreated(_, _, _, _, _) =>
      "ScenarioCreated"
    case ScenarioActivity.ScenarioArchived(_, _, _, _, _) =>
      "ScenarioArchived"
    case ScenarioActivity.ScenarioUnarchived(_, _, _, _, _) =>
      "ScenarioUnarchived"
    case ScenarioActivity.ScenarioModified(_, _, _, _, _, _, comment) =>
      s"ScenarioModified(comment=${stringify(comment)})"
    case ScenarioActivity.ScenarioNameChanged(_, _, _, _, _, oldName, newName) =>
      s"ScenarioNameChanged(oldName=$oldName,newName=$newName)"
    case ScenarioActivity.CommentAdded(_, _, _, _, _, comment) =>
      s"CommentAdded(comment=${stringify(comment)})"
    case ScenarioActivity.AttachmentAdded(_, _, _, _, _, attachment) =>
      s"AttachmentAdded(fileName=${stringify(attachment)})"
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

  private def stringify(attachment: ScenarioAttachment): String = attachment match {
    case ScenarioAttachment.Available(_, attachmentFilename, _, _) => s"Available(${attachmentFilename.value})"
    case ScenarioAttachment.Deleted(attachmentFilename, _, _)      => s"Deleted(${attachmentFilename.value})"
  }

  private def stringify(comment: ScenarioComment): String = comment match {
    case ScenarioComment.WithContent(comment, _, _) => comment
    case ScenarioComment.WithoutContent(_, _)       => "none"
  }

  private def stringify(result: DeploymentResult): String = result match {
    case DeploymentResult.Success(_)               => "Success"
    case DeploymentResult.Failure(_, errorMessage) => s"Failure($errorMessage)"
  }

  private def logWithContext(
      scenarioId: ScenarioId,
      scenarioVersionId: Option[ScenarioVersionId],
      username: String,
  )(log: String): IO[Unit] = IO.delay {
    MDC.clear()
    MDC.put("scenarioId", scenarioId.value.toString)
    MDC.put("scenarioVersionId", scenarioVersionId.map(_.value.toString).getOrElse("none"))
    MDC.put("username", username)
    logger.info(log)
    MDC.clear()
  }

}
