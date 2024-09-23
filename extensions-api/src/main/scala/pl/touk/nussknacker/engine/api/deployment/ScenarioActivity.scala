package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.component.ProcessingMode

import java.time.Instant
import java.util.UUID

final case class ScenarioId(value: Long) extends AnyVal

final case class ScenarioVersion(value: Long) extends AnyVal

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

sealed trait ScenarioActivity {
  def scenarioId: ScenarioId
  def scenarioActivityId: ScenarioActivityId
  def user: ScenarioUser
  def date: Instant
  def scenarioVersion: Option[ScenarioVersion]
}

object ScenarioActivity {

  final case class ScenarioCreated(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
  ) extends ScenarioActivity

  final case class ScenarioArchived(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
  ) extends ScenarioActivity

  final case class ScenarioUnarchived(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
  ) extends ScenarioActivity

  // Scenario deployments

  final case class ScenarioDeployed(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
      comment: ScenarioComment,
  ) extends ScenarioActivity

  final case class ScenarioPaused(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
      comment: ScenarioComment,
  ) extends ScenarioActivity

  final case class ScenarioCanceled(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
      comment: ScenarioComment,
  ) extends ScenarioActivity

  // Scenario modifications

  final case class ScenarioModified(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
      comment: ScenarioComment,
  ) extends ScenarioActivity

  final case class ScenarioNameChanged(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
      oldName: String,
      newName: String,
  ) extends ScenarioActivity

  final case class CommentAdded(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
      comment: ScenarioComment,
  ) extends ScenarioActivity

  final case class AttachmentAdded(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
      attachment: ScenarioAttachment,
  ) extends ScenarioActivity

  final case class ChangedProcessingMode(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
      from: ProcessingMode,
      to: ProcessingMode,
  ) extends ScenarioActivity

  // Migration between environments

  final case class IncomingMigration(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
      sourceEnvironment: Environment,
      sourceScenarioVersion: ScenarioVersion,
  ) extends ScenarioActivity

  final case class OutgoingMigration(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
      comment: ScenarioComment,
      destinationEnvironment: Environment,
  ) extends ScenarioActivity

  // Batch

  final case class PerformedSingleExecution(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
      comment: ScenarioComment,
      dateFinished: Option[Instant],
      errorMessage: Option[String],
  ) extends ScenarioActivity

  final case class PerformedScheduledExecution(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
      dateFinished: Option[Instant],
      errorMessage: Option[String],
  ) extends ScenarioActivity

  // Other/technical

  final case class AutomaticUpdate(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
      dateFinished: Instant,
      changes: String,
      errorMessage: Option[String],
  ) extends ScenarioActivity

  final case class CustomAction(
      scenarioId: ScenarioId,
      scenarioActivityId: ScenarioActivityId,
      user: ScenarioUser,
      date: Instant,
      scenarioVersion: Option[ScenarioVersion],
      actionName: String,
      comment: ScenarioComment,
  ) extends ScenarioActivity

}
