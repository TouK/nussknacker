package pl.touk.nussknacker.ui.process.repository.activities

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.Legacy
import pl.touk.nussknacker.ui.db.entity.AttachmentEntityData
import pl.touk.nussknacker.ui.process.ScenarioActivityAuditLog
import pl.touk.nussknacker.ui.process.ScenarioAttachmentService.AttachmentToAdd
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository.ModifyCommentError
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.dbio.DBIOAction

import java.time.Clock
import scala.concurrent.ExecutionContext

class ScenarioActivityRepositoryAuditLogDecorator(
    underlying: ScenarioActivityRepository
)(implicit executionContext: ExecutionContext)
    extends ScenarioActivityRepository {

  def clock: Clock = underlying.clock

  def addActivity(
      scenarioActivity: ScenarioActivity,
  ): DB[ScenarioActivityId] =
    underlying
      .addActivity(scenarioActivity)
      .map { scenarioActivityId =>
        ScenarioActivityAuditLog.onCreateScenarioActivity(scenarioActivity)
        scenarioActivityId
      }

  def editComment(
      scenarioId: ProcessId,
      rowId: Long,
      comment: String
  )(implicit user: LoggedUser): DB[Either[ModifyCommentError, ScenarioActivityId]] =
    underlying
      .editComment(scenarioId, rowId, comment)
      .map(_.map { scenarioActivityId =>
        ScenarioActivityAuditLog.onEditComment(scenarioId, user, scenarioActivityId, comment)
        scenarioActivityId
      })

  def editComment(
      scenarioId: ProcessId,
      activityId: ScenarioActivityId,
      comment: String
  )(implicit user: LoggedUser): DB[Either[ModifyCommentError, ScenarioActivityId]] =
    underlying
      .editComment(scenarioId, activityId, comment)
      .map(_.map { scenarioActivityId =>
        ScenarioActivityAuditLog.onEditComment(scenarioId, user, scenarioActivityId, comment)
        scenarioActivityId
      })

  def deleteComment(
      scenarioId: ProcessId,
      rowId: Long,
  )(implicit user: LoggedUser): DB[Either[ModifyCommentError, ScenarioActivityId]] =
    underlying
      .deleteComment(scenarioId, rowId)
      .map { scenarioActivityId =>
        ScenarioActivityAuditLog.onDeleteComment(scenarioId, rowId, user)
        scenarioActivityId
      }

  def deleteComment(
      scenarioId: ProcessId,
      activityId: ScenarioActivityId,
  )(implicit user: LoggedUser): DB[Either[ModifyCommentError, ScenarioActivityId]] =
    underlying
      .deleteComment(scenarioId, activityId)
      .map { scenarioActivityId =>
        ScenarioActivityAuditLog.onDeleteComment(scenarioId, activityId, user)
        scenarioActivityId
      }

  def addAttachment(
      attachmentToAdd: AttachmentToAdd
  )(implicit user: LoggedUser): DB[ScenarioActivityId] =
    underlying
      .addAttachment(attachmentToAdd)
      .andFinally(
        DBIOAction.successful(ScenarioActivityAuditLog.onAddAttachment(attachmentToAdd, user))
      )

  def findActivities(
      scenarioId: ProcessId,
  ): DB[Seq[ScenarioActivity]] = underlying.findActivities(scenarioId)

  def findAttachments(
      scenarioId: ProcessId,
  ): DB[Seq[AttachmentEntityData]] = underlying.findAttachments(scenarioId)

  def findAttachment(
      scenarioId: ProcessId,
      attachmentId: Long,
  ): DB[Option[AttachmentEntityData]] = underlying.findAttachment(scenarioId, attachmentId)

  def findActivity(
      processId: ProcessId
  ): DB[Legacy.ProcessActivity] = underlying.findActivity(processId)

  def getActivityStats: DB[Map[String, Int]] = underlying.getActivityStats

}
