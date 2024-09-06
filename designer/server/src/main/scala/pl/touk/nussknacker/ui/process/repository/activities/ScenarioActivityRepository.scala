package pl.touk.nussknacker.ui.process.repository.activities

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActivity, ScenarioActivityId}
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.Legacy
import pl.touk.nussknacker.ui.db.entity.AttachmentEntityData
import pl.touk.nussknacker.ui.process.ScenarioAttachmentService.AttachmentToAdd
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository.ModifyCommentError
import pl.touk.nussknacker.ui.security.api.LoggedUser

trait ScenarioActivityRepository {

  def findActivities(
      scenarioId: ProcessId,
  ): DB[Seq[ScenarioActivity]]

  def addActivity(
      scenarioActivity: ScenarioActivity,
  )(implicit user: LoggedUser): DB[ScenarioActivityId]

  def addComment(
      scenarioId: ProcessId,
      processVersionId: VersionId,
      comment: String
  )(implicit user: LoggedUser): DB[ScenarioActivityId]

  def editComment(
      scenarioActivityId: ScenarioActivityId,
      comment: String,
  )(implicit user: LoggedUser): DB[Either[ModifyCommentError, Unit]]

  def deleteComment(
      scenarioActivityId: ScenarioActivityId
  )(implicit user: LoggedUser): DB[Either[ModifyCommentError, Unit]]

  def addAttachment(
      attachmentToAdd: AttachmentToAdd
  )(implicit user: LoggedUser): DB[ScenarioActivityId]

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

}
