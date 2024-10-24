package pl.touk.nussknacker.ui.process.repository.activities

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.Legacy
import pl.touk.nussknacker.ui.db.entity.AttachmentEntityData
import pl.touk.nussknacker.ui.process.ScenarioAttachmentService.AttachmentToAdd
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository.{
  DeleteAttachmentError,
  ModifyCommentError
}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.LoggedUserUtils.Ops

import java.time.Clock

trait ScenarioActivityRepository {

  def clock: Clock

  def findActivities(
      scenarioId: ProcessId,
  ): DB[Seq[ScenarioActivity]]

  def addActivity(
      scenarioActivity: ScenarioActivity,
  ): DB[ScenarioActivityId]

  def addComment(
      scenarioId: ProcessId,
      processVersionId: VersionId,
      comment: String,
  )(implicit user: LoggedUser): DB[ScenarioActivityId] = {
    val now = clock.instant()
    addActivity(
      ScenarioActivity.CommentAdded(
        scenarioId = ScenarioId(scenarioId.value),
        scenarioActivityId = ScenarioActivityId.random,
        user = user.scenarioUser,
        date = now,
        scenarioVersionId = Some(ScenarioVersionId.from(processVersionId)),
        comment = ScenarioComment.Available(
          comment = comment,
          lastModifiedByUserName = UserName(user.username),
          lastModifiedAt = now,
        )
      ),
    )
  }

  def editComment(
      scenarioId: ProcessId,
      scenarioActivityId: ScenarioActivityId,
      comment: String,
  )(implicit user: LoggedUser): DB[Either[ModifyCommentError, ScenarioActivityId]]

  def editComment(
      scenarioId: ProcessId,
      commentId: Long,
      comment: String,
  )(implicit user: LoggedUser): DB[Either[ModifyCommentError, ScenarioActivityId]]

  def deleteComment(
      scenarioId: ProcessId,
      commentId: Long,
  )(implicit user: LoggedUser): DB[Either[ModifyCommentError, ScenarioActivityId]]

  def deleteComment(
      scenarioId: ProcessId,
      scenarioActivityId: ScenarioActivityId
  )(implicit user: LoggedUser): DB[Either[ModifyCommentError, ScenarioActivityId]]

  def addAttachment(
      attachmentToAdd: AttachmentToAdd
  )(implicit user: LoggedUser): DB[ScenarioActivityId]

  def markAttachmentAsDeleted(
      scenarioId: ProcessId,
      attachmentId: Long,
  )(implicit user: LoggedUser): DB[Either[DeleteAttachmentError, Unit]]

  def findAttachments(
      scenarioId: ProcessId,
  ): DB[Seq[AttachmentEntityData]]

  def findAttachment(
      scenarioId: ProcessId,
      attachmentId: Long,
  ): DB[Option[AttachmentEntityData]]

  def findActivity(
      processId: ProcessId
  ): DB[Legacy.ProcessActivity]

  def getActivityStats: DB[Map[String, Int]]

}

object ScenarioActivityRepository {

  sealed trait ModifyCommentError

  object ModifyCommentError {
    case object ActivityDoesNotExist  extends ModifyCommentError
    case object CommentDoesNotExist   extends ModifyCommentError
    case object CouldNotModifyComment extends ModifyCommentError
  }

  sealed trait DeleteAttachmentError

  object DeleteAttachmentError {
    case object CouldNotDeleteAttachment extends DeleteAttachmentError
  }

  sealed trait ModifyActivityError

  object ModifyActivityError {
    case object ActivityDoesNotExist   extends ModifyActivityError
    case object CouldNotModifyActivity extends ModifyActivityError
  }

}
