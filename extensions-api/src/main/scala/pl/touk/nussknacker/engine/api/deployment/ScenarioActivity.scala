package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.process.VersionId

import java.time.Instant
import java.util.UUID

final case class ScenarioId(value: Long) extends AnyVal

final case class ScenarioVersionId(value: Long) extends AnyVal

object ScenarioVersionId {
  def from(versionId: VersionId): ScenarioVersionId = ScenarioVersionId(versionId.value)
}

final case class ScenarioActivityId(value: UUID) extends AnyVal

object ScenarioActivityId {
  def random: ScenarioActivityId = ScenarioActivityId(UUID.randomUUID())
}

final case class ScenarioUser(
    id: Option[UserId],
    name: UserName,
    impersonatedByUserId: Option[UserId],
    impersonatedByUserName: Option[UserName],
)

object ScenarioUser {
  val internalNuUser: ScenarioUser = ScenarioUser(None, UserName("Nussknacker"), None, None)
}

final case class UserId(value: String)
final case class UserName(value: String)

sealed trait ScenarioComment

object ScenarioComment {

  final case class Available(
      comment: String,
      lastModifiedByUserName: UserName,
      lastModifiedAt: Instant,
  ) extends ScenarioComment

  final case class Deleted(
      deletedByUserName: UserName,
      deletedAt: Instant,
  ) extends ScenarioComment

}

sealed trait ScenarioAttachment

object ScenarioAttachment {

  final case class Available(
      attachmentId: AttachmentId,
      attachmentFilename: AttachmentFilename,
      lastModifiedByUserName: UserName,
      lastModifiedAt: Instant,
  ) extends ScenarioAttachment

  final case class Deleted(
      attachmentFilename: AttachmentFilename,
      deletedByUserName: UserName,
      deletedAt: Instant,
  ) extends ScenarioAttachment

  final case class AttachmentId(value: Long)         extends AnyVal
  final case class AttachmentFilename(value: String) extends AnyVal
}

final case class Environment(name: String) extends AnyVal

sealed trait ScheduledExecutionStatus

object ScheduledExecutionStatus {
  case object Scheduled extends ScheduledExecutionStatus

  case object Deployed extends ScheduledExecutionStatus

  case object Finished extends ScheduledExecutionStatus

  case object Failed extends ScheduledExecutionStatus

  case object DeploymentWillBeRetried extends ScheduledExecutionStatus

  case object DeploymentFailed extends ScheduledExecutionStatus
}

sealed trait ScenarioActivity {
  def scenarioId: ScenarioId
  def scenarioActivityId: ScenarioActivityId
  def user: ScenarioUser
  def date: Instant
  def scenarioVersionId: Option[ScenarioVersionId]
}

object ScenarioActivity {

  final case class ScenarioCreated(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
  ) extends ScenarioActivity

  final case class ScenarioArchived(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
  ) extends ScenarioActivity

  final case class ScenarioUnarchived(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
  ) extends ScenarioActivity

  // Scenario deployments

  final case class ScenarioDeployed(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
      comment: ScenarioComment,
  ) extends ScenarioActivity

  final case class ScenarioPaused(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
      comment: ScenarioComment,
  ) extends ScenarioActivity

  final case class ScenarioCanceled(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
      comment: ScenarioComment,
  ) extends ScenarioActivity

  // Scenario modifications

  final case class ScenarioModified(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
      comment: ScenarioComment,
  ) extends ScenarioActivity

  final case class ScenarioNameChanged(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
      oldName: String,
      newName: String,
  ) extends ScenarioActivity

  final case class CommentAdded(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
      comment: ScenarioComment,
  ) extends ScenarioActivity

  final case class AttachmentAdded(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
      attachment: ScenarioAttachment,
  ) extends ScenarioActivity

  final case class ChangedProcessingMode(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
      from: ProcessingMode,
      to: ProcessingMode,
  ) extends ScenarioActivity

  // Migration between environments

  final case class IncomingMigration(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
      sourceEnvironment: Environment,
      sourceUser: UserName,
      sourceScenarioVersionId: Option[ScenarioVersionId],
      targetEnvironment: Option[Environment],
  ) extends ScenarioActivity

  final case class OutgoingMigration(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
      destinationEnvironment: Environment,
  ) extends ScenarioActivity

  // Batch

  final case class PerformedSingleExecution(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
      comment: ScenarioComment,
      dateFinished: Option[Instant],
      status: Option[String],
      errorMessage: Option[String],
  ) extends ScenarioActivity

  final case class PerformedScheduledExecution(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
      dateFinished: Option[Instant],
      scheduleName: String,
      status: ScheduledExecutionStatus,
      createdAt: Instant,
      nextRetryAt: Option[Instant],
      retriesLeft: Option[Int],
  ) extends ScenarioActivity

  // Other/technical

  final case class AutomaticUpdate(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
      changes: String,
      errorMessage: Option[String],
  ) extends ScenarioActivity

  final case class CustomAction(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersionId: Option[ScenarioVersionId],
      actionName: String,
      comment: ScenarioComment,
  ) extends ScenarioActivity

}
