package pl.touk.nussknacker.ui.process.repository.activities

import cats.implicits.catsSyntaxEitherId
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.Comment
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.ScenarioAttachment.{AttachmentFilename, AttachmentId}
import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo
import pl.touk.nussknacker.engine.api.process.ProcessId
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.Legacy
import pl.touk.nussknacker.ui.db.{DbRef, NuTables}
import pl.touk.nussknacker.ui.db.entity.{
  AdditionalProperties,
  AttachmentEntityData,
  ScenarioActivityEntityData,
  ScenarioActivityType
}
import pl.touk.nussknacker.ui.process.ScenarioAttachmentService.AttachmentToAdd
import pl.touk.nussknacker.ui.process.repository.DbioRepository
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository.{
  CommentModificationMetadata,
  DeleteAttachmentError,
  ModifyCommentError
}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.statistics.{AttachmentsTotal, CommentsTotal}
import pl.touk.nussknacker.ui.util.LoggedUserUtils.Ops
import pl.touk.nussknacker.ui.util.ScenarioActivityUtils.ScenarioActivityOps

import java.sql.Timestamp
import java.time.{Clock, Instant}
import scala.concurrent.ExecutionContext
import scala.util.Try

class DbScenarioActivityRepository private (override protected val dbRef: DbRef, override val clock: Clock)(
    implicit executionContext: ExecutionContext,
) extends DbioRepository
    with NuTables
    with ScenarioActivityRepository
    with LazyLogging {

  import dbRef.profile.api._

  def findActivities(
      scenarioId: ProcessId,
      after: Option[Instant],
  ): DB[Seq[ScenarioActivity]] = {
    doFindActivities(scenarioId, after).map(_.map(_._2))
  }

  def addActivity(
      scenarioActivity: ScenarioActivity,
  ): DB[ScenarioActivityId] = {
    insertActivity(scenarioActivity).map(_.activityId)
  }

  def editComment(
      scenarioId: ProcessId,
      activityId: ScenarioActivityId,
      validate: CommentModificationMetadata => Either[ModifyCommentError, Unit],
      comment: String,
  )(implicit user: LoggedUser): DB[Either[ModifyCommentError, ScenarioActivityId]] = {
    modifyActivityByActivityId(
      activityId = activityId,
      activityDoesNotExistError = ModifyCommentError.ActivityDoesNotExist,
      validateCurrentValue = entity =>
        for {
          _ <- validateThatActivityIsAssignedToScenario(scenarioId, entity)
          _ <- validate(commentModificationMetadata(entity))
        } yield (),
      modify = doEditComment(comment),
      couldNotModifyError = ModifyCommentError.CouldNotModifyComment,
    )
  }

  def deleteComment(
      scenarioId: ProcessId,
      rowId: Long,
      validate: CommentModificationMetadata => Either[ModifyCommentError, Unit],
  )(implicit user: LoggedUser): DB[Either[ModifyCommentError, ScenarioActivityId]] = {
    modifyActivityByRowId(
      rowId = rowId,
      activityDoesNotExistError = ModifyCommentError.ActivityDoesNotExist,
      validateCurrentValue = entity =>
        for {
          _ <- validateThatActivityIsAssignedToScenario(scenarioId, entity)
          _ <- validate(commentModificationMetadata(entity))
        } yield (),
      modify = doDeleteComment,
      couldNotModifyError = ModifyCommentError.CouldNotModifyComment,
    )
  }

  def deleteComment(
      scenarioId: ProcessId,
      activityId: ScenarioActivityId,
      validate: CommentModificationMetadata => Either[ModifyCommentError, Unit],
  )(implicit user: LoggedUser): DB[Either[ModifyCommentError, ScenarioActivityId]] = {
    modifyActivityByActivityId(
      activityId = activityId,
      activityDoesNotExistError = ModifyCommentError.ActivityDoesNotExist,
      validateCurrentValue = entity =>
        for {
          _ <- validateThatActivityIsAssignedToScenario(scenarioId, entity)
          _ <- validate(commentModificationMetadata(entity))
        } yield (),
      modify = doDeleteComment,
      couldNotModifyError = ModifyCommentError.CouldNotModifyComment,
    )
  }

  def addAttachment(
      attachmentToAdd: AttachmentToAdd
  )(implicit user: LoggedUser): DB[ScenarioActivityId] = {
    val now = clock.instant()
    for {
      attachment <- attachmentInsertQuery += AttachmentEntityData(
        id = -1L,
        processId = attachmentToAdd.scenarioId,
        processVersionId = attachmentToAdd.scenarioVersionId,
        fileName = attachmentToAdd.fileName,
        data = attachmentToAdd.data,
        user = user.username,
        impersonatedByIdentity = user.impersonatingUserId,
        impersonatedByUsername = user.impersonatingUserName,
        createDate = Timestamp.from(now)
      )
      activity <- insertActivity(
        ScenarioActivity.AttachmentAdded(
          scenarioId = ScenarioId(attachmentToAdd.scenarioId.value),
          scenarioActivityId = ScenarioActivityId.random,
          user = user.scenarioUser,
          date = now,
          scenarioVersionId = Some(ScenarioVersionId.from(attachmentToAdd.scenarioVersionId)),
          attachment = ScenarioAttachment.Available(
            attachmentId = AttachmentId(attachment.id),
            attachmentFilename = AttachmentFilename(attachmentToAdd.fileName),
            lastModifiedByUserName = UserName(user.username),
            lastModifiedAt = now,
          )
        ),
      )
    } yield activity.activityId
  }

  def findAttachments(
      scenarioId: ProcessId,
  ): DB[Seq[AttachmentEntityData]] = {
    scenarioActivityTable
      .filter(entity =>
        entity.scenarioId === scenarioId && entity.activityType.inSet(Set(ScenarioActivityType.AttachmentAdded))
      )
      .join(attachmentsTable)
      .on(_.attachmentId === _.id)
      .map(_._2)
      .result
  }

  def markAttachmentAsDeleted(
      scenarioId: ProcessId,
      attachmentId: Long,
  )(implicit user: LoggedUser): DB[Either[DeleteAttachmentError, Unit]] = {
    scenarioActivityTable
      .filter(entity =>
        entity.scenarioId === scenarioId &&
          entity.attachmentId === attachmentId &&
          entity.activityType === (ScenarioActivityType.AttachmentAdded: ScenarioActivityType)
      )
      .map(_.attachmentId)
      .update(Option.empty[Long])
      .map { updateCount =>
        if (updateCount > 0) {
          Right(())
        } else {
          Left(DeleteAttachmentError.CouldNotDeleteAttachment)
        }
      }
  }

  def findAttachment(
      scenarioId: ProcessId,
      attachmentId: Long,
  ): DB[Option[AttachmentEntityData]] = {
    scenarioActivityTable
      .filter(entity =>
        entity.scenarioId === scenarioId && entity.activityType.inSet(Set(ScenarioActivityType.AttachmentAdded))
      )
      .join(attachmentsTable.filter(_.id === attachmentId))
      .on(_.attachmentId === _.id)
      .map(_._2)
      .result
      .headOption
  }

  def findActivity(
      processId: ProcessId
  ): DB[Legacy.ProcessActivity] = {
    for {
      attachmentEntities <- findAttachments(processId)
      attachments = attachmentEntities.map(toDto)
      activities <- doFindActivities(processId, after = None)
      comments = activities.flatMap { case (id, activity) => toComment(id, activity) }
    } yield Legacy.ProcessActivity(
      comments = comments.toList,
      attachments = attachments.toList,
    )

  }

  def getActivityStats: DB[Map[String, Int]] = {
    val findScenarioProcessActivityStats = for {
      attachmentsTotal <- attachmentsTable.length.result
      commentsTotal    <- scenarioActivityTable.filter(_.comment.isDefined).length.result
    } yield Map(
      AttachmentsTotal -> attachmentsTotal,
      CommentsTotal    -> commentsTotal,
    ).map { case (k, v) => (k.toString, v) }
    run(findScenarioProcessActivityStats)
  }

  private def doFindActivities(
      scenarioId: ProcessId,
      after: Option[Instant],
  ): DB[Seq[(Long, ScenarioActivity)]] = {
    scenarioActivityTable
      .filter(_.scenarioId === scenarioId)
      .filterOpt(after)((table, after) => table.createdAt > Timestamp.from(after))
      // ScenarioActivity in domain represents a single, immutable event, so we interpret only finished operations as ScenarioActivities
      .filter { entity =>
        entity.state.isEmpty ||
        entity.state === ProcessActionState.Finished ||
        entity.state === ProcessActionState.ExecutionFinished
      }
      .sortBy(_.createdAt)
      .result
      .map { entity =>
        entity.map(fromEntity).flatMap {
          case Left(error) =>
            logger.warn(s"Ignoring invalid scenario activity: [$error] for $entity")
            None
          case Right(activity) =>
            Some(activity)
        }
      }
  }

  private def validateThatActivityIsAssignedToScenario(
      scenarioId: ProcessId,
      entity: ScenarioActivityEntityData
  ): Either[ModifyCommentError, Unit] = {
    for {
      _ <- Either.cond(entity.scenarioId == scenarioId, (), ModifyCommentError.CommentDoesNotExist)
    } yield ()
  }

  private def toComment(
      id: Long,
      scenarioActivity: ScenarioActivity,
      comment: ScenarioComment,
      prefix: Option[String]
  ): Option[Legacy.Comment] = {
    for {
      scenarioVersion <- scenarioActivity.scenarioVersionId
      content <- comment match {
        case ScenarioComment.WithContent(comment, _, _) => Some(comment)
        case ScenarioComment.WithoutContent(_, _)       => None
      }
    } yield Legacy.Comment(
      id = id,
      processVersionId = scenarioVersion.value,
      content = prefix.getOrElse("") + content,
      user = scenarioActivity.user.name.value,
      createDate = scenarioActivity.date,
    )
  }

  private def toComment(id: Long, scenarioActivity: ScenarioActivity): Option[Legacy.Comment] = {
    scenarioActivity match {
      case _: ScenarioActivity.ScenarioCreated =>
        None
      case _: ScenarioActivity.ScenarioArchived =>
        None
      case _: ScenarioActivity.ScenarioUnarchived =>
        None
      case activity: ScenarioActivity.ScenarioDeployed =>
        toComment(id, activity, activity.comment, Some("Deployment: "))
      case activity: ScenarioActivity.ScenarioPaused =>
        toComment(id, activity, activity.comment, Some("Pause: "))
      case activity: ScenarioActivity.ScenarioCanceled =>
        toComment(id, activity, activity.comment, Some("Stop: "))
      case activity: ScenarioActivity.ScenarioModified =>
        toComment(id, activity, activity.comment, None)
      case activity: ScenarioActivity.ScenarioNameChanged =>
        toComment(
          id,
          activity,
          ScenarioComment.from(s"Rename: [${activity.oldName}] -> [${activity.newName}]", UserName(""), activity.date),
          None
        )
      case activity: ScenarioActivity.CommentAdded =>
        toComment(id, activity, activity.comment, None)
      case _: ScenarioActivity.AttachmentAdded =>
        None
      case _: ScenarioActivity.ChangedProcessingMode =>
        None
      case activity: ScenarioActivity.IncomingMigration =>
        toComment(
          id,
          activity,
          ScenarioComment.from(
            content = s"Scenario migrated from ${activity.sourceEnvironment.name} by ${activity.sourceUser.value}",
            lastModifiedByUserName = activity.user.name,
            lastModifiedAt = activity.date
          ),
          None,
        )
      case _: ScenarioActivity.OutgoingMigration =>
        None
      case activity: ScenarioActivity.PerformedSingleExecution =>
        toComment(id, activity, activity.comment, Some("Run now: "))
      case _: ScenarioActivity.PerformedScheduledExecution =>
        None
      case activity: ScenarioActivity.AutomaticUpdate =>
        toComment(
          id,
          activity,
          ScenarioComment.from(
            content = s"Migrations applied: ${activity.changes}",
            lastModifiedByUserName = activity.user.name,
            lastModifiedAt = activity.date
          ),
          None,
        )
      case _: ScenarioActivity.CustomAction =>
        None
    }
  }

  private lazy val activityByRowIdCompiled = Compiled { rowId: Rep[Long] =>
    scenarioActivityTable.filter(_.id === rowId)
  }

  private lazy val activityByIdCompiled = Compiled { activityId: Rep[ScenarioActivityId] =>
    scenarioActivityTable.filter(_.activityId === activityId)
  }

  private lazy val attachmentInsertQuery =
    attachmentsTable returning attachmentsTable.map(_.id) into ((item, id) => item.copy(id = id))

  private def modifyActivityByActivityId[ERROR](
      activityId: ScenarioActivityId,
      activityDoesNotExistError: ERROR,
      validateCurrentValue: ScenarioActivityEntityData => Either[ERROR, Unit],
      modify: ScenarioActivityEntityData => ScenarioActivityEntityData,
      couldNotModifyError: ERROR,
  ): DB[Either[ERROR, ScenarioActivityId]] = {
    doModifyActivity[ScenarioActivityId, ERROR](
      key = activityId,
      fetchActivity = activityByIdCompiled(_).result.headOption,
      updateRow = (id: ScenarioActivityId, updatedEntity) => activityByIdCompiled(id).update(updatedEntity),
      activityDoesNotExistError = activityDoesNotExistError,
      validateCurrentValue = validateCurrentValue,
      modify = modify,
      couldNotModifyError = couldNotModifyError
    )
  }

  private def modifyActivityByRowId[ERROR](
      rowId: Long,
      activityDoesNotExistError: ERROR,
      validateCurrentValue: ScenarioActivityEntityData => Either[ERROR, Unit],
      modify: ScenarioActivityEntityData => ScenarioActivityEntityData,
      couldNotModifyError: ERROR,
  ): DB[Either[ERROR, ScenarioActivityId]] = {
    doModifyActivity[Long, ERROR](
      key = rowId,
      fetchActivity = activityByRowIdCompiled(_).result.headOption,
      updateRow = (id: Long, updatedEntity) => activityByRowIdCompiled(id).update(updatedEntity),
      activityDoesNotExistError = activityDoesNotExistError,
      validateCurrentValue = validateCurrentValue,
      modify = modify,
      couldNotModifyError = couldNotModifyError
    )
  }

  private def doModifyActivity[KEY, ERROR](
      key: KEY,
      fetchActivity: KEY => DB[Option[ScenarioActivityEntityData]],
      updateRow: (KEY, ScenarioActivityEntityData) => DB[Int],
      activityDoesNotExistError: ERROR,
      validateCurrentValue: ScenarioActivityEntityData => Either[ERROR, Unit],
      modify: ScenarioActivityEntityData => ScenarioActivityEntityData,
      couldNotModifyError: ERROR,
  ): DB[Either[ERROR, ScenarioActivityId]] = {
    val action = for {
      fetchedActivity <- fetchActivity(key)
      result <- {
        val modifiedEntity = for {
          entity <- fetchedActivity.toRight(activityDoesNotExistError)
          _      <- validateCurrentValue(entity)
          modifiedEntity = modify(entity)
        } yield modifiedEntity

        modifiedEntity match {
          case Left(error) =>
            DBIO.successful(Left(error))
          case Right(modifiedEntity) =>
            for {
              rowsAffected <- updateRow(key, modifiedEntity)
              res <- DBIO.successful(Either.cond(rowsAffected != 0, modifiedEntity.activityId, couldNotModifyError))
            } yield res
        }
      }
    } yield result
    action.transactionally
  }

  private def insertActivity(
      activity: ScenarioActivity,
  ): DB[ScenarioActivityEntityData] = {
    val entity = toEntity(activity)
    (scenarioActivityTable += entity).map { insertCount =>
      if (insertCount == 1) {
        entity
      } else {
        throw new RuntimeException(s"Unable to insert activity")
      }
    }
  }

  private def doEditComment(comment: String)(
      entity: ScenarioActivityEntityData
  )(implicit user: LoggedUser): ScenarioActivityEntityData = {
    val now = clock.instant()
    entity.copy(
      comment = Some(comment),
      lastModifiedByUserName = Some(user.username),
      lastModifiedAt = Some(Timestamp.from(now)),
      additionalProperties = entity.additionalProperties.withProperty(
        key = s"comment_replaced_by_${user.username}_at_$now",
        value = entity.comment.getOrElse(""),
      )
    )
  }

  private def doDeleteComment(
      entity: ScenarioActivityEntityData
  )(implicit user: LoggedUser): ScenarioActivityEntityData = {
    entity.copy(
      comment = None,
      lastModifiedByUserName = Some(user.username),
      additionalProperties = entity.additionalProperties.withProperty(
        key = s"comment_deleted_by_${user.username}_at_${clock.instant()}",
        value = entity.comment.getOrElse(""),
      )
    )
  }

  private def commentModificationMetadata(entity: ScenarioActivityEntityData): CommentModificationMetadata = {
    val isScenarioDeployedActivity = entity.activityType match {
      case ScenarioActivityType.ScenarioDeployed => true
      case _                                     => false
    }
    CommentModificationMetadata(commentForScenarioDeployed = isScenarioDeployedActivity)
  }

  private def createEntity(scenarioActivity: ScenarioActivity)(
      attachmentId: Option[Long] = None,
      comment: Option[String] = None,
      lastModifiedByUserName: Option[String] = None,
      additionalProperties: AdditionalProperties = AdditionalProperties.empty,
  ): ScenarioActivityEntityData = {
    val now = Timestamp.from(clock.instant())
    ScenarioActivityEntityData(
      id = -1,
      activityType = scenarioActivity.activityType,
      scenarioId = ProcessId(scenarioActivity.scenarioId.value),
      activityId = scenarioActivity.scenarioActivityId,
      userId = scenarioActivity.user.id.map(_.value),
      userName = scenarioActivity.user.name.value,
      impersonatedByUserId = scenarioActivity.user.impersonatedByUserId.map(_.value),
      impersonatedByUserName = scenarioActivity.user.impersonatedByUserName.map(_.value),
      lastModifiedByUserName = lastModifiedByUserName,
      lastModifiedAt = Some(now),
      createdAt = now,
      scenarioVersion = scenarioActivity.scenarioVersionId,
      comment = comment,
      attachmentId = attachmentId,
      finishedAt = scenarioActivity.dateFinishedOpt.map(Timestamp.from),
      state = Some(ProcessActionState.Finished),
      errorMessage = scenarioActivity match {
        case activity: DeploymentRelatedActivity =>
          activity.result match {
            case DeploymentResult.Success(_)               => None
            case DeploymentResult.Failure(_, errorMessage) => errorMessage
          }
        case _ => None
      },
      additionalProperties = additionalProperties,
    )
  }

  private def comment(scenarioComment: ScenarioComment): Option[String] = {
    scenarioComment match {
      case ScenarioComment.WithContent(comment, _, _) => Some(comment.content)
      case ScenarioComment.WithoutContent(_, _)       => None
    }
  }

  private def lastModifiedByUserName(scenarioComment: ScenarioComment): Option[String] = {
    val userName = scenarioComment match {
      case ScenarioComment.WithContent(_, lastModifiedByUserName, _) => lastModifiedByUserName
      case ScenarioComment.WithoutContent(deletedByUserName, _)      => deletedByUserName
    }
    Some(userName.value)
  }

  private def lastModifiedByUserName(scenarioAttachment: ScenarioAttachment): Option[String] = {
    val userName = scenarioAttachment match {
      case ScenarioAttachment.Available(_, _, lastModifiedByUserName, _) =>
        Some(lastModifiedByUserName.value)
      case ScenarioAttachment.Deleted(_, deletedByUserName, _) =>
        Some(deletedByUserName.value)
    }
    Some(userName.value)
  }

  private def toEntity(scenarioActivity: ScenarioActivity): ScenarioActivityEntityData = {
    scenarioActivity match {
      case _: ScenarioActivity.ScenarioCreated =>
        createEntity(scenarioActivity)()
      case _: ScenarioActivity.ScenarioArchived =>
        createEntity(scenarioActivity)()
      case _: ScenarioActivity.ScenarioUnarchived =>
        createEntity(scenarioActivity)()
      case activity: ScenarioActivity.ScenarioDeployed =>
        createEntity(scenarioActivity)(
          comment = comment(activity.comment),
          lastModifiedByUserName = lastModifiedByUserName(activity.comment),
        )
      case activity: ScenarioActivity.ScenarioPaused =>
        createEntity(scenarioActivity)(
          comment = comment(activity.comment),
          lastModifiedByUserName = lastModifiedByUserName(activity.comment),
        )
      case activity: ScenarioActivity.ScenarioCanceled =>
        createEntity(scenarioActivity)(
          comment = comment(activity.comment),
          lastModifiedByUserName = lastModifiedByUserName(activity.comment),
        )
      case activity: ScenarioActivity.ScenarioModified =>
        createEntity(scenarioActivity)(
          comment = comment(activity.comment),
          lastModifiedByUserName = lastModifiedByUserName(activity.comment),
          additionalProperties = AdditionalProperties(
            List(
              activity.previousScenarioVersionId.map(_.value.toString).map("prevVersionId" -> _),
            ).flatten.toMap
          )
        )
      case activity: ScenarioActivity.ScenarioNameChanged =>
        createEntity(scenarioActivity)(
          additionalProperties = AdditionalProperties(
            Map(
              "oldName" -> activity.oldName,
              "newName" -> activity.newName,
            )
          )
        )
      case activity: ScenarioActivity.CommentAdded =>
        createEntity(scenarioActivity)(
          comment = comment(activity.comment),
          lastModifiedByUserName = lastModifiedByUserName(activity.comment),
          additionalProperties = AdditionalProperties.empty,
        )
      case activity: ScenarioActivity.AttachmentAdded =>
        val (attachmentId, attachmentFilename) = activity.attachment match {
          case ScenarioAttachment.Available(id, filename, _, _) => (Some(id.value), Some(filename.value))
          case ScenarioAttachment.Deleted(filename, _, _)       => (None, Some(filename.value))
        }
        createEntity(scenarioActivity)(
          attachmentId = attachmentId,
          lastModifiedByUserName = lastModifiedByUserName(activity.attachment),
          additionalProperties = AdditionalProperties(
            attachmentFilename.map("attachmentFilename" -> _).toMap
          )
        )
      case activity: ScenarioActivity.ChangedProcessingMode =>
        createEntity(scenarioActivity)(
          additionalProperties = AdditionalProperties(
            Map(
              "fromProcessingMode" -> activity.from.entryName,
              "toProcessingMode"   -> activity.to.entryName,
            )
          )
        )
      case activity: ScenarioActivity.IncomingMigration =>
        createEntity(scenarioActivity)(
          additionalProperties = AdditionalProperties(
            List(
              Some("sourceEnvironment" -> activity.sourceEnvironment.name),
              Some("sourceUser"        -> activity.sourceUser.value),
              activity.targetEnvironment.map(v => "targetEnvironment" -> v.name),
              activity.sourceScenarioVersionId.map(v => "sourceScenarioVersion" -> v.toString),
            ).flatten.toMap
          )
        )
      case activity: ScenarioActivity.OutgoingMigration =>
        createEntity(scenarioActivity)(
          additionalProperties = AdditionalProperties(
            Map(
              "destinationEnvironment" -> activity.destinationEnvironment.name,
            )
          )
        )
      case activity: ScenarioActivity.PerformedSingleExecution =>
        createEntity(scenarioActivity)()
      case activity: ScenarioActivity.PerformedScheduledExecution =>
        createEntity(scenarioActivity)(
          additionalProperties = AdditionalProperties(
            List(
              Some("scheduleName" -> activity.scheduleName),
              Some("status"       -> activity.scheduledExecutionStatus.entryName),
              Some("createdAt"    -> activity.createdAt.toString),
              activity.nextRetryAt.map(nra => "nextRetryAt" -> nra.toString),
              activity.retriesLeft.map(rl => "retriesLeft" -> rl.toString)
            ).flatten.toMap
          )
        )
      case activity: ScenarioActivity.AutomaticUpdate =>
        createEntity(scenarioActivity)(
          additionalProperties = AdditionalProperties(
            Map(
              "description" -> activity.changes,
            )
          )
        )
      case activity: ScenarioActivity.CustomAction =>
        createEntity(scenarioActivity)(
          comment = comment(activity.comment),
          lastModifiedByUserName = lastModifiedByUserName(activity.comment),
        )
    }
  }

  private def userFromEntity(entity: ScenarioActivityEntityData): ScenarioUser = {
    ScenarioUser(
      id = entity.userId.map(UserId),
      name = UserName(entity.userName),
      impersonatedByUserId = entity.impersonatedByUserId.map(UserId.apply),
      impersonatedByUserName = entity.impersonatedByUserName.map(UserName.apply),
    )
  }

  private def scenarioIdFromEntity(entity: ScenarioActivityEntityData): ScenarioId = {
    ScenarioId(entity.scenarioId.value)
  }

  private def resultFromEntity(entity: ScenarioActivityEntityData): Either[String, DeploymentResult] = {
    for {
      state        <- entity.state.toRight("Missing state field")
      dateFinished <- entity.finishedAt.toRight("Missing finishedAt field").map(_.toInstant)
      result <- state match {
        case ProcessActionState.InProgress        => Left("InProgress is not a terminal state")
        case ProcessActionState.Finished          => Right(DeploymentResult.Success(dateFinished))
        case ProcessActionState.Failed            => Right(DeploymentResult.Failure(dateFinished, entity.errorMessage))
        case ProcessActionState.ExecutionFinished => Right(DeploymentResult.Success(dateFinished))
      }
    } yield result
  }

  private def commentFromEntity(entity: ScenarioActivityEntityData): Either[String, ScenarioComment] = {
    for {
      lastModifiedByUserName <- entity.lastModifiedByUserName.toRight("Missing lastModifiedByUserName field")
      lastModifiedAt         <- entity.lastModifiedAt.toRight("Missing lastModifiedAt field")
    } yield ScenarioComment.from(
      content = entity.comment.flatMap(Comment.from),
      lastModifiedByUserName = UserName(lastModifiedByUserName),
      lastModifiedAt = lastModifiedAt.toInstant
    )
  }

  private def attachmentFromEntity(entity: ScenarioActivityEntityData): Either[String, ScenarioAttachment] = {
    for {
      lastModifiedByUserName <- entity.lastModifiedByUserName.toRight("Missing lastModifiedByUserName field")
      filename               <- additionalPropertyFromEntity(entity, "attachmentFilename")
      lastModifiedAt         <- entity.lastModifiedAt.toRight("Missing lastModifiedAt field")
    } yield {
      entity.attachmentId match {
        case Some(id) =>
          ScenarioAttachment.Available(
            attachmentId = AttachmentId(id),
            attachmentFilename = AttachmentFilename(filename),
            lastModifiedByUserName = UserName(lastModifiedByUserName),
            lastModifiedAt = lastModifiedAt.toInstant,
          )
        case None =>
          ScenarioAttachment.Deleted(
            attachmentFilename = AttachmentFilename(filename),
            deletedByUserName = UserName(lastModifiedByUserName),
            deletedAt = lastModifiedAt.toInstant,
          )
      }
    }
  }

  private def additionalPropertyFromEntity(entity: ScenarioActivityEntityData, name: String): Either[String, String] = {
    optionalAdditionalPropertyFromEntity(entity, name).toRight(s"Missing additional property $name")
  }

  private def optionalAdditionalPropertyFromEntity(entity: ScenarioActivityEntityData, name: String): Option[String] = {
    entity.additionalProperties.properties.get(name)
  }

  private def fromEntity(entity: ScenarioActivityEntityData): Either[String, (Long, ScenarioActivity)] = {
    entity.activityType match {
      case ScenarioActivityType.ScenarioCreated =>
        ScenarioActivity
          .ScenarioCreated(
            scenarioId = scenarioIdFromEntity(entity),
            scenarioActivityId = entity.activityId,
            user = userFromEntity(entity),
            date = entity.createdAt.toInstant,
            scenarioVersionId = entity.scenarioVersion
          )
          .asRight
          .map((entity.id, _))
      case ScenarioActivityType.ScenarioArchived =>
        ScenarioActivity
          .ScenarioArchived(
            scenarioId = scenarioIdFromEntity(entity),
            scenarioActivityId = entity.activityId,
            user = userFromEntity(entity),
            date = entity.createdAt.toInstant,
            scenarioVersionId = entity.scenarioVersion
          )
          .asRight
          .map((entity.id, _))
      case ScenarioActivityType.ScenarioUnarchived =>
        ScenarioActivity
          .ScenarioUnarchived(
            scenarioId = scenarioIdFromEntity(entity),
            scenarioActivityId = entity.activityId,
            user = userFromEntity(entity),
            date = entity.createdAt.toInstant,
            scenarioVersionId = entity.scenarioVersion
          )
          .asRight
          .map((entity.id, _))
      case ScenarioActivityType.ScenarioDeployed =>
        (for {
          comment <- commentFromEntity(entity)
          result  <- resultFromEntity(entity)
        } yield ScenarioActivity.ScenarioDeployed(
          scenarioId = scenarioIdFromEntity(entity),
          scenarioActivityId = entity.activityId,
          user = userFromEntity(entity),
          date = entity.createdAt.toInstant,
          scenarioVersionId = entity.scenarioVersion,
          comment = comment,
          result = result,
        )).map((entity.id, _))
      case ScenarioActivityType.ScenarioPaused =>
        (for {
          comment <- commentFromEntity(entity)
          result  <- resultFromEntity(entity)
        } yield ScenarioActivity.ScenarioPaused(
          scenarioId = scenarioIdFromEntity(entity),
          scenarioActivityId = entity.activityId,
          user = userFromEntity(entity),
          date = entity.createdAt.toInstant,
          scenarioVersionId = entity.scenarioVersion,
          comment = comment,
          result = result,
        )).map((entity.id, _))
      case ScenarioActivityType.ScenarioCanceled =>
        (for {
          comment <- commentFromEntity(entity)
          result  <- resultFromEntity(entity)
        } yield ScenarioActivity.ScenarioCanceled(
          scenarioId = scenarioIdFromEntity(entity),
          scenarioActivityId = entity.activityId,
          user = userFromEntity(entity),
          date = entity.createdAt.toInstant,
          scenarioVersionId = entity.scenarioVersion,
          comment = comment,
          result = result,
        )).map((entity.id, _))
      case ScenarioActivityType.ScenarioModified =>
        (for {
          comment <- commentFromEntity(entity)
          previousScenarioVersionId = optionalAdditionalPropertyFromEntity(entity, "prevVersionId")
            .flatMap(toLongOption)
            .map(ScenarioVersionId.apply)
        } yield ScenarioActivity.ScenarioModified(
          scenarioId = scenarioIdFromEntity(entity),
          scenarioActivityId = entity.activityId,
          user = userFromEntity(entity),
          date = entity.createdAt.toInstant,
          previousScenarioVersionId = previousScenarioVersionId,
          scenarioVersionId = entity.scenarioVersion,
          comment = comment,
        ))
          .map((entity.id, _))
      case ScenarioActivityType.ScenarioNameChanged =>
        (for {
          oldName <- additionalPropertyFromEntity(entity, "oldName")
          newName <- additionalPropertyFromEntity(entity, "newName")
        } yield ScenarioActivity.ScenarioNameChanged(
          scenarioId = scenarioIdFromEntity(entity),
          scenarioActivityId = entity.activityId,
          user = userFromEntity(entity),
          date = entity.createdAt.toInstant,
          scenarioVersionId = entity.scenarioVersion,
          oldName = oldName,
          newName = newName,
        )).map((entity.id, _))
      case ScenarioActivityType.CommentAdded =>
        (for {
          comment <- commentFromEntity(entity)
        } yield ScenarioActivity.CommentAdded(
          scenarioId = scenarioIdFromEntity(entity),
          scenarioActivityId = entity.activityId,
          user = userFromEntity(entity),
          date = entity.createdAt.toInstant,
          scenarioVersionId = entity.scenarioVersion,
          comment = comment,
        )).map((entity.id, _))
      case ScenarioActivityType.AttachmentAdded =>
        (for {
          attachment <- attachmentFromEntity(entity)
        } yield ScenarioActivity.AttachmentAdded(
          scenarioId = scenarioIdFromEntity(entity),
          scenarioActivityId = entity.activityId,
          user = userFromEntity(entity),
          date = entity.createdAt.toInstant,
          scenarioVersionId = entity.scenarioVersion,
          attachment = attachment,
        )).map((entity.id, _))
      case ScenarioActivityType.ChangedProcessingMode =>
        (for {
          from <- additionalPropertyFromEntity(entity, "fromProcessingMode").flatMap(
            ProcessingMode.withNameEither(_).left.map(_.getMessage())
          )
          to <- additionalPropertyFromEntity(entity, "toProcessingMode").flatMap(
            ProcessingMode.withNameEither(_).left.map(_.getMessage())
          )
        } yield ScenarioActivity.ChangedProcessingMode(
          scenarioId = scenarioIdFromEntity(entity),
          scenarioActivityId = entity.activityId,
          user = userFromEntity(entity),
          date = entity.createdAt.toInstant,
          scenarioVersionId = entity.scenarioVersion,
          from = from,
          to = to,
        )).map((entity.id, _))
      case ScenarioActivityType.IncomingMigration =>
        (for {
          sourceEnvironment <- additionalPropertyFromEntity(entity, "sourceEnvironment")
          sourceUser        <- additionalPropertyFromEntity(entity, "sourceUser")
          targetEnvironment = optionalAdditionalPropertyFromEntity(entity, "targetEnvironment")
          sourceScenarioVersion = optionalAdditionalPropertyFromEntity(entity, "sourceScenarioVersion").flatMap(
            toLongOption
          )
        } yield ScenarioActivity.IncomingMigration(
          scenarioId = scenarioIdFromEntity(entity),
          scenarioActivityId = entity.activityId,
          user = userFromEntity(entity),
          date = entity.createdAt.toInstant,
          scenarioVersionId = entity.scenarioVersion,
          sourceEnvironment = Environment(sourceEnvironment),
          sourceUser = UserName(sourceUser),
          sourceScenarioVersionId = sourceScenarioVersion.map(ScenarioVersionId.apply),
          targetEnvironment = targetEnvironment.map(Environment),
        )).map((entity.id, _))
      case ScenarioActivityType.OutgoingMigration =>
        (for {
          destinationEnvironment <- additionalPropertyFromEntity(entity, "destinationEnvironment")
        } yield ScenarioActivity.OutgoingMigration(
          scenarioId = scenarioIdFromEntity(entity),
          scenarioActivityId = entity.activityId,
          user = userFromEntity(entity),
          date = entity.createdAt.toInstant,
          scenarioVersionId = entity.scenarioVersion,
          destinationEnvironment = Environment(destinationEnvironment),
        )).map((entity.id, _))
      case ScenarioActivityType.PerformedSingleExecution =>
        (for {
          comment <- commentFromEntity(entity)
          result  <- resultFromEntity(entity)
        } yield ScenarioActivity.PerformedSingleExecution(
          scenarioId = scenarioIdFromEntity(entity),
          scenarioActivityId = entity.activityId,
          user = userFromEntity(entity),
          date = entity.createdAt.toInstant,
          scenarioVersionId = entity.scenarioVersion,
          comment = comment,
          result = result,
        )).map((entity.id, _))
      case ScenarioActivityType.PerformedScheduledExecution =>
        (for {
          scheduleName <- additionalPropertyFromEntity(entity, "scheduleName")
          scheduledExecutionStatus <- additionalPropertyFromEntity(entity, "status").flatMap(
            ScheduledExecutionStatus.withNameEither(_).left.map(_.getMessage)
          )
          dateFinished <- entity.finishedAt.toRight("Missing finishedAt field").map(_.toInstant)
          createdAt    <- additionalPropertyFromEntity(entity, "createdAt").map(Instant.parse)
          nextRetryAt = optionalAdditionalPropertyFromEntity(entity, "nextRetryAt").map(Instant.parse)
          retriesLeft = optionalAdditionalPropertyFromEntity(entity, "retriesLeft").flatMap(toIntOption)
        } yield ScenarioActivity.PerformedScheduledExecution(
          scenarioId = scenarioIdFromEntity(entity),
          scenarioActivityId = entity.activityId,
          user = userFromEntity(entity),
          date = entity.createdAt.toInstant,
          scenarioVersionId = entity.scenarioVersion,
          dateFinished = dateFinished,
          scheduleName = scheduleName,
          scheduledExecutionStatus = scheduledExecutionStatus,
          createdAt = createdAt,
          nextRetryAt = nextRetryAt,
          retriesLeft = retriesLeft,
        )).map((entity.id, _))
      case ScenarioActivityType.AutomaticUpdate =>
        (for {
          description <- additionalPropertyFromEntity(entity, "description")
        } yield ScenarioActivity.AutomaticUpdate(
          scenarioId = scenarioIdFromEntity(entity),
          scenarioActivityId = entity.activityId,
          user = userFromEntity(entity),
          date = entity.createdAt.toInstant,
          scenarioVersionId = entity.scenarioVersion,
          changes = description,
        )).map((entity.id, _))

      case ScenarioActivityType.CustomAction(actionName) =>
        (for {
          comment <- commentFromEntity(entity)
          result  <- resultFromEntity(entity)
        } yield ScenarioActivity
          .CustomAction(
            scenarioId = scenarioIdFromEntity(entity),
            scenarioActivityId = entity.activityId,
            user = userFromEntity(entity),
            date = entity.createdAt.toInstant,
            scenarioVersionId = entity.scenarioVersion,
            actionName = actionName,
            comment = comment,
            result = result,
          )).map((entity.id, _))
    }
  }

  private def toDto(attachmentEntityData: AttachmentEntityData): Legacy.Attachment = {
    Legacy.Attachment(
      id = attachmentEntityData.id,
      processVersionId = attachmentEntityData.processVersionId.value,
      fileName = attachmentEntityData.fileName,
      user = attachmentEntityData.user,
      createDate = attachmentEntityData.createDateTime,
    )
  }

  private def toLongOption(str: String) = Try(str.toLong).toOption

  private def toIntOption(str: String) = Try(str.toInt).toOption

}

object DbScenarioActivityRepository {

  def create(dbRef: DbRef, clock: Clock)(
      implicit executionContext: ExecutionContext,
  ): ScenarioActivityRepository = {
    new ScenarioActivityRepositoryAuditLogDecorator(
      new DbScenarioActivityRepository(dbRef, clock)
    )
  }

}
