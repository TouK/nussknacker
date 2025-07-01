package pl.touk.nussknacker.ui.process.periodic.flink.db

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActivity, ScenarioActivityId}
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.Legacy
import pl.touk.nussknacker.ui.db.entity.AttachmentEntityData
import pl.touk.nussknacker.ui.process.ScenarioAttachmentService
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.dbio.DBIO

import java.time.{Clock, Instant}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class InMemScenarioActivityRepository extends ScenarioActivityRepository {

  private val acitivities: mutable.ListBuffer[ScenarioActivity] = ListBuffer.empty

  def getActivities: List[ScenarioActivity] = synchronized {
    acitivities.toList
  }

  override def addActivity(
      scenarioActivity: ScenarioActivity
  ): DB[ScenarioActivityId] = DBIO.successful {
    acitivities.addOne(scenarioActivity)
    scenarioActivity.scenarioActivityId
  }

  override def clock: Clock = ???

  override def findActivities(
      scenarioId: ProcessId,
      after: Option[Instant]
  ): DB[Seq[ScenarioActivity]] = ???

  override def editComment(
      scenarioId: ProcessId,
      scenarioActivityId: ScenarioActivityId,
      validate: ScenarioActivityRepository.CommentModificationMetadata => Either[
        ScenarioActivityRepository.ModifyCommentError,
        Unit
      ],
      comment: String
  )(implicit user: LoggedUser): DB[Either[ScenarioActivityRepository.ModifyCommentError, ScenarioActivityId]] = ???

  override def deleteComment(
      scenarioId: ProcessId,
      commentId: Long,
      validate: ScenarioActivityRepository.CommentModificationMetadata => Either[
        ScenarioActivityRepository.ModifyCommentError,
        Unit
      ]
  )(implicit user: LoggedUser): DB[Either[ScenarioActivityRepository.ModifyCommentError, ScenarioActivityId]] = ???

  override def deleteComment(
      scenarioId: ProcessId,
      scenarioActivityId: ScenarioActivityId,
      validate: ScenarioActivityRepository.CommentModificationMetadata => Either[
        ScenarioActivityRepository.ModifyCommentError,
        Unit
      ]
  )(implicit user: LoggedUser): DB[Either[ScenarioActivityRepository.ModifyCommentError, ScenarioActivityId]] = ???

  override def addAttachment(
      attachmentToAdd: ScenarioAttachmentService.AttachmentToAdd
  )(implicit user: LoggedUser): DB[ScenarioActivityId] = ???

  override def markAttachmentAsDeleted(
      scenarioId: ProcessId,
      attachmentId: Long,
  )(implicit user: LoggedUser): DB[Either[ScenarioActivityRepository.DeleteAttachmentError, Unit]] = ???

  override def findAttachments(
      scenarioId: ProcessId,
  ): DB[Seq[AttachmentEntityData]] = ???

  override def findAttachment(
      scenarioId: ProcessId,
      attachmentId: Long,
  ): DB[Option[AttachmentEntityData]] = ???

  override def findActivity(
      processId: ProcessId,
  ): DB[Legacy.ProcessActivity] = ???

  override def getActivityStats: DB[Map[String, Int]] = ???
}
