package pl.touk.nussknacker.ui.api.description.scenarioActivity

import derevo.circe.{decoder, encoder}
import derevo.derive
import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.UpperSnakecase
import io.circe
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import pl.touk.nussknacker.engine.api.deployment.ScheduledExecutionStatus
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.api.TapirCodecs.enumSchema
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivity.AdditionalField
import pl.touk.nussknacker.ui.server.HeadersSupport.FileName
import sttp.model.MediaType
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.generic.Configuration

import java.io.InputStream
import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.collection.immutable

object Dtos {

  sealed trait ScenarioType extends EnumEntry

  object ScenarioType extends Enum[ScenarioType] {
    case object Scenario extends ScenarioType
    case object Fragment extends ScenarioType

    override def values = findValues
  }

  @derive(encoder, decoder, schema)
  final case class ScenarioActivitiesMetadata(
      activities: List[ScenarioActivityMetadata],
      actions: List[ScenarioActivityActionMetadata],
  )

  object ScenarioActivitiesMetadata {

    def default(scenarioType: ScenarioType): ScenarioActivitiesMetadata = ScenarioActivitiesMetadata(
      activities = ScenarioActivityType.values.map(ScenarioActivityMetadata.from(scenarioType)).toList,
      actions = List(
        ScenarioActivityActionMetadata(
          id = "compare",
          displayableName = "Compare",
          icon = "/assets/activities/actions/compare.svg"
        ),
        ScenarioActivityActionMetadata(
          id = "delete_comment",
          displayableName = "Delete comment",
          icon = "/assets/activities/actions/delete.svg"
        ),
        ScenarioActivityActionMetadata(
          id = "add_comment",
          displayableName = "Add comment",
          icon = "/assets/activities/actions/add_comment.svg"
        ),
        ScenarioActivityActionMetadata(
          id = "edit_comment",
          displayableName = "Edit comment",
          icon = "/assets/activities/actions/edit.svg"
        ),
        ScenarioActivityActionMetadata(
          id = "download_attachment",
          displayableName = "Download",
          icon = "/assets/activities/actions/download.svg"
        ),
        ScenarioActivityActionMetadata(
          id = "delete_attachment",
          displayableName = "Delete",
          icon = "/assets/activities/actions/delete.svg"
        ),
      )
    )

  }

  @derive(encoder, decoder, schema)
  final case class ScenarioActivityActionMetadata(
      id: String,
      displayableName: String,
      icon: String,
  )

  @derive(encoder, decoder, schema)
  final case class ScenarioActivityMetadata(
      `type`: String,
      displayableName: String,
      icon: String,
      supportedActions: List[String],
  )

  object ScenarioActivityMetadata {

    def from(scenarioType: ScenarioType)(scenarioActivityType: ScenarioActivityType): ScenarioActivityMetadata =
      ScenarioActivityMetadata(
        `type` = scenarioActivityType.entryName,
        displayableName = scenarioType match {
          case ScenarioType.Scenario => scenarioActivityType.displayableNameForScenario
          case ScenarioType.Fragment => scenarioActivityType.displayableNameForFragment
        },
        icon = scenarioActivityType.icon,
        supportedActions = scenarioActivityType.supportedActions,
      )

  }

  sealed trait ScenarioActivityType extends EnumEntry with UpperSnakecase {
    def displayableNameForScenario: String
    def displayableNameForFragment: String
    def icon: String
    def supportedActions: List[String]
  }

  object ScenarioActivityType extends Enum[ScenarioActivityType] {

    private val commentRelatedActions = List("delete_comment", "edit_comment", "add_comment")

    case object ScenarioCreated extends ScenarioActivityType {
      override def displayableNameForScenario: String = s"Scenario created"
      override def displayableNameForFragment: String = s"Fragment created"
      override def icon: String                       = "/assets/activities/scenarioModified.svg"
      override def supportedActions: List[String]     = List.empty
    }

    case object ScenarioArchived extends ScenarioActivityType {
      override def displayableNameForScenario: String = s"Scenario archived"
      override def displayableNameForFragment: String = s"Fragment archived"
      override def icon: String                       = "/assets/activities/archived.svg"
      override def supportedActions: List[String]     = List.empty
    }

    case object ScenarioUnarchived extends ScenarioActivityType {
      override def displayableNameForScenario: String = s"Scenario unarchived"
      override def displayableNameForFragment: String = s"Fragment unarchived"
      override def icon: String                       = "/assets/activities/unarchived.svg"
      override def supportedActions: List[String]     = List.empty
    }

    case object ScenarioDeployed extends ScenarioActivityType {
      private val displayableName: String             = "Deployment"
      override def displayableNameForScenario: String = displayableName
      override def displayableNameForFragment: String = displayableName
      override def icon: String                       = "/assets/activities/deployed.svg"
      override def supportedActions: List[String]     = commentRelatedActions
    }

    case object ScenarioPaused extends ScenarioActivityType {
      private val displayableName: String             = "Pause"
      override def displayableNameForScenario: String = displayableName
      override def displayableNameForFragment: String = displayableName
      override def icon: String                       = "/assets/activities/pause.svg"
      override def supportedActions: List[String]     = commentRelatedActions
    }

    case object ScenarioCanceled extends ScenarioActivityType {
      private val displayableName: String             = "Cancel"
      override def displayableNameForScenario: String = displayableName
      override def displayableNameForFragment: String = displayableName
      override def icon: String                       = "/assets/activities/cancel.svg"
      override def supportedActions: List[String]     = commentRelatedActions
    }

    case object ScenarioModified extends ScenarioActivityType {
      override def displayableNameForScenario: String = s"Scenario modified"
      override def displayableNameForFragment: String = s"Fragment modified"
      override def icon: String                       = "/assets/activities/scenarioModified.svg"
      override def supportedActions: List[String]     = commentRelatedActions ::: "compare" :: Nil
    }

    case object ScenarioNameChanged extends ScenarioActivityType {
      override def displayableNameForScenario: String = s"Scenario name changed"
      override def displayableNameForFragment: String = s"Fragment name changed"
      override def icon: String                       = "/assets/activities/scenarioModified.svg"
      override def supportedActions: List[String]     = List.empty
    }

    case object CommentAdded extends ScenarioActivityType {
      private val displayableName: String             = "Comment"
      override def displayableNameForScenario: String = displayableName
      override def displayableNameForFragment: String = displayableName
      override def icon: String                       = "/assets/activities/comment.svg"
      override def supportedActions: List[String]     = commentRelatedActions
    }

    case object AttachmentAdded extends ScenarioActivityType {
      private val displayableName: String             = "Attachment"
      override def displayableNameForScenario: String = displayableName
      override def displayableNameForFragment: String = displayableName
      override def icon: String                       = "/assets/activities/attachment.svg"
      override def supportedActions: List[String]     = List("download_attachment", "delete_attachment")
    }

    case object ChangedProcessingMode extends ScenarioActivityType {
      private val displayableName: String             = "Processing mode change"
      override def displayableNameForScenario: String = displayableName
      override def displayableNameForFragment: String = displayableName
      override def icon: String                       = "/assets/activities/processingModeChange.svg"
      override def supportedActions: List[String]     = List.empty
    }

    case object IncomingMigration extends ScenarioActivityType {
      private val displayableName: String             = "Incoming migration"
      override def displayableNameForScenario: String = displayableName
      override def displayableNameForFragment: String = displayableName
      override def icon: String                       = "/assets/activities/migration.svg"
      override def supportedActions: List[String]     = List("compare")
    }

    case object OutgoingMigration extends ScenarioActivityType {
      private val displayableName: String             = "Outgoing migration"
      override def displayableNameForScenario: String = displayableName
      override def displayableNameForFragment: String = displayableName
      override def icon: String                       = "/assets/activities/migration.svg"
      override def supportedActions: List[String]     = List.empty
    }

    case object PerformedSingleExecution extends ScenarioActivityType {
      private val displayableName: String             = "Processing data"
      override def displayableNameForScenario: String = displayableName
      override def displayableNameForFragment: String = displayableName
      override def icon: String                       = "/assets/activities/processingData.svg"
      override def supportedActions: List[String]     = commentRelatedActions
    }

    case object PerformedScheduledExecution extends ScenarioActivityType {
      private val displayableName: String             = "Processing data"
      override def displayableNameForScenario: String = displayableName
      override def displayableNameForFragment: String = displayableName
      override def icon: String                       = "/assets/activities/processingData.svg"
      override def supportedActions: List[String]     = List.empty
    }

    case object AutomaticUpdate extends ScenarioActivityType {
      private val displayableName: String             = "Automatic update"
      override def displayableNameForScenario: String = displayableName
      override def displayableNameForFragment: String = displayableName
      override def icon: String                       = "/assets/activities/automaticUpdate.svg"
      override def supportedActions: List[String]     = List("compare")
    }

    case object CustomAction extends ScenarioActivityType {
      private val displayableName: String             = "Custom action"
      override def displayableNameForScenario: String = displayableName
      override def displayableNameForFragment: String = displayableName
      override def icon: String                       = "/assets/activities/customAction.svg"
      override def supportedActions: List[String]     = List.empty
    }

    override def values: immutable.IndexedSeq[ScenarioActivityType] = findValues

    implicit def scenarioActivityTypeSchema: Schema[ScenarioActivityType] =
      enumSchema[ScenarioActivityType](
        ScenarioActivityType.values.toList,
        _.entryName,
      )

    implicit def scenarioActivityTypeCodec: circe.Codec[ScenarioActivityType] = circe.Codec.from(
      Decoder.decodeString.emap(str =>
        ScenarioActivityType.withNameEither(str).left.map(_ => s"Invalid scenario action type [$str]")
      ),
      Encoder.encodeString.contramap(_.entryName),
    )

    implicit def scenarioActivityTypeTextCodec: Codec[String, ScenarioActivityType, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.fromDecode[String, ScenarioActivityType] {
          ScenarioActivityType.withNameOption(_) match {
            case Some(value) => DecodeResult.Value(value)
            case None        => DecodeResult.InvalidValue(Nil)
          }
        }(_.entryName)
      )

  }

  @derive(encoder, decoder, schema)
  final case class ScenarioActivityComment(
      content: ScenarioActivityCommentContent,
      lastModifiedBy: String,
      lastModifiedAt: Instant
  )

  sealed trait ScenarioActivityCommentContent

  object ScenarioActivityCommentContent {

    implicit def scenarioActivityAttachmentFileCodec: circe.Codec[ScenarioActivityCommentContent] = {
      implicit val configuration: extras.Configuration =
        extras.Configuration.default.withDiscriminator("status").withScreamingSnakeCaseConstructorNames
      deriveConfiguredCodec
    }

    implicit def scenarioActivityAttachmentFileSchema: Schema[ScenarioActivityCommentContent] = {
      implicit val configuration: Configuration =
        Configuration.default.withDiscriminator("status").withScreamingSnakeCaseDiscriminatorValues
      Schema.derived[ScenarioActivityCommentContent]
    }

    final case class Available(value: String) extends ScenarioActivityCommentContent

    case object NotAvailable extends ScenarioActivityCommentContent

  }

  @derive(encoder, decoder, schema)
  final case class ScenarioActivityAttachment(
      file: ScenarioActivityAttachmentFile,
      filename: String,
      lastModifiedBy: String,
      lastModifiedAt: Instant
  )

  sealed trait ScenarioActivityAttachmentFile

  object ScenarioActivityAttachmentFile {

    implicit def scenarioActivityAttachmentFileCodec: circe.Codec[ScenarioActivityAttachmentFile] = {
      implicit val configuration: extras.Configuration =
        extras.Configuration.default.withDiscriminator("status").withScreamingSnakeCaseConstructorNames
      deriveConfiguredCodec
    }

    implicit def scenarioActivityAttachmentFileSchema: Schema[ScenarioActivityAttachmentFile] = {
      implicit val configuration: Configuration =
        Configuration.default.withDiscriminator("status").withScreamingSnakeCaseDiscriminatorValues
      Schema.derived[ScenarioActivityAttachmentFile]
    }

    final case class Available(id: Long) extends ScenarioActivityAttachmentFile

    case object Deleted extends ScenarioActivityAttachmentFile

  }

  @derive(encoder, decoder, schema)
  final case class ScenarioActivities(activities: List[ScenarioActivity])

  final case class ScenarioActivity(
      id: UUID,
      user: String,
      date: Instant,
      scenarioVersionId: Option[Long],
      comment: Option[ScenarioActivityComment],
      attachment: Option[ScenarioActivityAttachment],
      additionalFields: List[AdditionalField],
      overrideIcon: Option[String] = None,
      overrideDisplayableName: Option[String] = None,
      overrideSupportedActions: Option[List[String]] = None,
      `type`: ScenarioActivityType,
  )

  object ScenarioActivity {

    implicit def scenarioActivityCodec: circe.Codec[ScenarioActivity] = {
      implicit val configuration: extras.Configuration =
        extras.Configuration.default.withDiscriminator("type").withScreamingSnakeCaseConstructorNames
      val derivedCodec = deriveConfiguredCodec[ScenarioActivity]
      circe.Codec.from(
        decodeA = derivedCodec,
        encodeA = derivedCodec.mapJson(_.dropNullValues)
      )
    }

    implicit def scenarioActivitySchema: Schema[ScenarioActivity] = {
      implicit val configuration: Configuration =
        Configuration.default.withDiscriminator("type").withScreamingSnakeCaseDiscriminatorValues
      Schema.derived[ScenarioActivity]
    }

    @derive(encoder, decoder, schema)
    final case class AdditionalField(
        name: String,
        value: String
    )

    def forScenarioCreated(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioCreated,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = None,
      attachment = None,
      additionalFields = List.empty,
    )

    def forScenarioArchived(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioArchived,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = None,
      attachment = None,
      additionalFields = List.empty,
    )

    def forScenarioUnarchived(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioUnarchived,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = None,
      attachment = None,
      additionalFields = List.empty,
    )

    // Scenario deployments

    def forScenarioDeployed(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        comment: ScenarioActivityComment,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioDeployed,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = Some(comment),
      attachment = None,
      additionalFields = List.empty,
    )

    def forScenarioPaused(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        comment: ScenarioActivityComment,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioPaused,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = Some(comment),
      attachment = None,
      additionalFields = List.empty,
    )

    def forScenarioCanceled(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        comment: ScenarioActivityComment,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioCanceled,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = Some(comment),
      attachment = None,
      additionalFields = List.empty,
    )

    // Scenario modifications

    def forScenarioModified(
        id: UUID,
        user: String,
        date: Instant,
        previousScenarioVersionId: Option[Long],
        scenarioVersionId: Option[Long],
        comment: ScenarioActivityComment,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioModified,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = Some(comment),
      attachment = None,
      additionalFields = List.empty,
      overrideDisplayableName = updatedVersionId(previousScenarioVersionId, scenarioVersionId).map(updatedVersion =>
        s"Version $updatedVersion saved"
      )
    )

    private def updatedVersionId(oldVersionIdOpt: Option[Long], newVersionIdOpt: Option[Long]) = {
      for {
        newVersionId <- newVersionIdOpt
        oldVersionIdOrZero = oldVersionIdOpt.getOrElse(0L)
        updatedVersionId <- if (newVersionId > oldVersionIdOrZero) Some(newVersionId) else None
      } yield updatedVersionId
    }

    def forScenarioNameChanged(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        oldName: String,
        newName: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioNameChanged,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = None,
      attachment = None,
      additionalFields = List(
        AdditionalField("oldName", oldName),
        AdditionalField("newName", newName),
      )
    )

    def forCommentAdded(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        comment: ScenarioActivityComment,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.CommentAdded,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = Some(comment),
      attachment = None,
      additionalFields = List.empty,
    )

    def forAttachmentAdded(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        attachment: ScenarioActivityAttachment,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.AttachmentAdded,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = None,
      attachment = Some(attachment),
      additionalFields = List.empty,
      overrideDisplayableName = attachment.file match {
        case ScenarioActivityAttachmentFile.Available(_) => Some(attachment.filename)
        case ScenarioActivityAttachmentFile.Deleted      => Some("File removed")
      },
    )

    def forChangedProcessingMode(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        from: String,
        to: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ChangedProcessingMode,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = None,
      attachment = None,
      additionalFields = List(
        AdditionalField("from", from),
        AdditionalField("to", to),
      )
    )

    // Migration between environments

    def forIncomingMigration(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        sourceEnvironment: String,
        sourceUser: String,
        sourceScenarioVersionId: Option[Long],
        targetEnvironment: Option[String],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.IncomingMigration,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = None,
      attachment = None,
      additionalFields = List(
        Some(AdditionalField("sourceEnvironment", sourceEnvironment)),
        Some(AdditionalField("sourceUser", sourceUser)),
        targetEnvironment.map(v => AdditionalField("targetEnvironment", v)),
        sourceScenarioVersionId.map(v => AdditionalField("sourceScenarioVersionId", v.toString)),
      ).flatten
    )

    def forOutgoingMigration(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        destinationEnvironment: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.OutgoingMigration,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = None,
      attachment = None,
      additionalFields = List(
        AdditionalField("destinationEnvironment", destinationEnvironment),
      )
    )

    // Batch

    def forPerformedSingleExecution(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        comment: ScenarioActivityComment,
        dateFinished: Instant,
        errorMessage: Option[String],
    )(implicit zoneId: ZoneId): ScenarioActivity = {
      val humanReadableStatus = "Run now execution finished"
      ScenarioActivity(
        id = id,
        `type` = ScenarioActivityType.PerformedSingleExecution,
        user = user,
        date = date,
        scenarioVersionId = scenarioVersionId,
        comment = Some(comment),
        attachment = None,
        additionalFields = List(
          Some(AdditionalField("status", humanReadableStatus)),
          Some(AdditionalField("started", format(date))),
          Some(AdditionalField("finished", format(dateFinished))),
          errorMessage.map(e => AdditionalField("errorMessage", e)),
        ).flatten
      )
    }

    def forPerformedScheduledExecution(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        dateFinished: Instant,
        scheduleName: String,
        scheduledExecutionStatus: ScheduledExecutionStatus,
        createdAt: Instant,
        nextRetryAt: Option[Instant],
        retriesLeft: Option[Int],
    )(implicit zoneId: ZoneId): ScenarioActivity = {
      val humanReadableStatus = scheduledExecutionStatus match {
        case ScheduledExecutionStatus.Finished                => "Scheduled execution finished"
        case ScheduledExecutionStatus.Failed                  => "Scheduled execution failed"
        case ScheduledExecutionStatus.DeploymentWillBeRetried => "Deployment will be retried"
        case ScheduledExecutionStatus.DeploymentFailed        => "Deployment failed"
      }
      ScenarioActivity(
        id = id,
        `type` = ScenarioActivityType.PerformedScheduledExecution,
        user = user,
        date = date,
        scenarioVersionId = scenarioVersionId,
        comment = None,
        attachment = None,
        additionalFields = List(
          Some(AdditionalField("status", humanReadableStatus)),
          Some(AdditionalField("created", format(createdAt))),
          Some(AdditionalField("started", format(date))),
          Some(AdditionalField("finished", format(dateFinished))),
          Some(AdditionalField("scheduleName", scheduleName)),
          retriesLeft.map(rl => AdditionalField("retriesLeft", rl.toString)),
          nextRetryAt.map(nra => AdditionalField("nextRetryAt", format(nra))),
        ).flatten
      )
    }

    // Other/technical

    def forAutomaticUpdate(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        changes: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.AutomaticUpdate,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = Some(
        ScenarioActivityComment(
          content = ScenarioActivityCommentContent.Available(changes),
          lastModifiedBy = user,
          lastModifiedAt = date
        )
      ),
      attachment = None,
      additionalFields = List.empty,
    )

    def forCustomAction(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        comment: ScenarioActivityComment,
        actionName: String,
        customIcon: Option[String],
        errorMessage: Option[String],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.CustomAction,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = Some(comment),
      attachment = None,
      additionalFields = List(
        Some(AdditionalField("actionName", actionName)),
        errorMessage.map(e => AdditionalField("errorMessage", e)),
      ).flatten,
      overrideIcon = customIcon,
    )

  }

  @derive(encoder, decoder, schema)
  final case class ScenarioAttachments(attachments: List[Attachment])

  @derive(encoder, decoder, schema)
  final case class Comment private (
      id: Long,
      scenarioVersion: Long,
      content: String,
      user: String,
      createDate: Instant
  )

  @derive(encoder, decoder, schema)
  final case class Attachment private (
      id: Long,
      scenarioVersion: Long,
      fileName: String,
      user: String,
      createDate: Instant
  )

  final case class AddCommentRequest(scenarioName: ProcessName, versionId: VersionId, commentContent: String)

  final case class DeprecatedEditCommentRequest(
      scenarioName: ProcessName,
      commentId: Long,
      commentContent: String
  )

  final case class EditCommentRequest(
      scenarioName: ProcessName,
      scenarioActivityId: UUID,
      commentContent: String
  )

  final case class DeleteCommentRequest(
      scenarioName: ProcessName,
      scenarioActivityId: UUID
  )

  final case class DeprecatedDeleteCommentRequest(
      scenarioName: ProcessName,
      commentId: Long,
  )

  final case class AddAttachmentRequest(
      scenarioName: ProcessName,
      versionId: VersionId,
      body: InputStream,
      fileName: FileName
  )

  final case class DeleteAttachmentRequest(scenarioName: ProcessName, attachmentId: Long)

  final case class GetAttachmentRequest(scenarioName: ProcessName, attachmentId: Long)

  final case class GetAttachmentResponse(inputStream: InputStream, fileName: Option[String], contentType: String)

  object GetAttachmentResponse {
    val emptyResponse: GetAttachmentResponse =
      GetAttachmentResponse(InputStream.nullInputStream(), None, MediaType.TextPlainUtf8.toString())
  }

  sealed trait ScenarioActivityError

  object ScenarioActivityError {

    final case class NoScenario(scenarioName: ProcessName) extends ScenarioActivityError
    final case object NoPermission                         extends ScenarioActivityError with CustomAuthorizationError
    final case class NoActivity(scenarioActivityId: UUID)  extends ScenarioActivityError
    final case class NoComment(commentId: Long)            extends ScenarioActivityError
    final case class NoAttachment(attachmentId: Long)      extends ScenarioActivityError
    final case class InvalidComment(error: String)         extends ScenarioActivityError

    implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario](e => s"No scenario ${e.scenarioName} found")

    implicit val noCommentCodec: Codec[String, NoComment, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoComment](e =>
        s"Unable to delete comment with id: ${e.commentId}"
      )

    implicit val invalidCommentCodec: Codec[String, InvalidComment, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[InvalidComment](_.error)

    implicit val noAttachmentCodec: Codec[String, NoAttachment, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoAttachment](e =>
        s"Unable to delete attachment with id: ${e.attachmentId}"
      )

    implicit val noActivityCodec: Codec[String, NoActivity, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoActivity](e =>
        s"Unable to delete comment for activity with id: ${e.scenarioActivityId.toString}"
      )

  }

  object Legacy {

    @derive(encoder, decoder, schema)
    final case class ProcessActivity private (comments: List[Comment], attachments: List[Attachment])

    @derive(encoder, decoder, schema)
    final case class Comment(
        id: Long,
        processVersionId: Long,
        content: String,
        user: String,
        createDate: Instant
    )

    @derive(encoder, decoder, schema)
    final case class Attachment(
        id: Long,
        processVersionId: Long,
        fileName: String,
        user: String,
        createDate: Instant
    )

  }

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  private def format(instant: Instant)(implicit zoneId: ZoneId): String = {
    instant.atZone(zoneId).format(dateTimeFormatter)
  }

}
