package pl.touk.nussknacker.ui.api.description.scenarioActivity

import derevo.circe.{decoder, encoder}
import derevo.derive
import enumeratum.EnumEntry.UpperSnakecase
import enumeratum.{Enum, EnumEntry}
import io.circe
import io.circe.generic.extras
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import io.circe.{Decoder, Encoder}
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
import java.time.Instant
import java.util.UUID
import scala.collection.immutable

object Dtos {

  @derive(encoder, decoder, schema)
  final case class ScenarioActivitiesMetadata(
      activities: List[ScenarioActivityMetadata],
      actions: List[ScenarioActivityActionMetadata],
  )

  object ScenarioActivitiesMetadata {

    val default: ScenarioActivitiesMetadata = ScenarioActivitiesMetadata(
      activities = ScenarioActivityType.values.map(ScenarioActivityMetadata.from).toList,
      actions = List(
        ScenarioActivityActionMetadata(
          id = "compare",
          displayableName = "Compare",
          icon = "/assets/activities/actions/compare.svg"
        ),
        ScenarioActivityActionMetadata(
          id = "delete_comment",
          displayableName = "Delete",
          icon = "/assets/activities/actions/delete.svg"
        ),
        ScenarioActivityActionMetadata(
          id = "edit_comment",
          displayableName = "Edit",
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

    def from(scenarioActivityType: ScenarioActivityType): ScenarioActivityMetadata =
      ScenarioActivityMetadata(
        `type` = scenarioActivityType.entryName,
        displayableName = scenarioActivityType.displayableName,
        icon = scenarioActivityType.icon,
        supportedActions = scenarioActivityType.supportedActions,
      )

  }

  sealed trait ScenarioActivityType extends EnumEntry with UpperSnakecase {
    def displayableName: String
    def icon: String
    def supportedActions: List[String]
  }

  object ScenarioActivityType extends Enum[ScenarioActivityType] {

    private val commentRelatedActions = List("delete_comment", "edit_comment")

    case object ScenarioCreated extends ScenarioActivityType {
      override def displayableName: String        = "Scenario created"
      override def icon: String                   = "/assets/activities/scenarioModified.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object ScenarioArchived extends ScenarioActivityType {
      override def displayableName: String        = "Scenario archived"
      override def icon: String                   = "/assets/activities/archived.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object ScenarioUnarchived extends ScenarioActivityType {
      override def displayableName: String        = "Scenario unarchived"
      override def icon: String                   = "/assets/activities/unarchived.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object ScenarioDeployed extends ScenarioActivityType {
      override def displayableName: String        = "Deployment"
      override def icon: String                   = "/assets/activities/deployed.svg"
      override def supportedActions: List[String] = commentRelatedActions
    }

    case object ScenarioPaused extends ScenarioActivityType {
      override def displayableName: String        = "Pause"
      override def icon: String                   = "/assets/activities/pause.svg"
      override def supportedActions: List[String] = commentRelatedActions
    }

    case object ScenarioCanceled extends ScenarioActivityType {
      override def displayableName: String        = "Cancel"
      override def icon: String                   = "/assets/activities/cancel.svg"
      override def supportedActions: List[String] = commentRelatedActions
    }

    case object ScenarioModified extends ScenarioActivityType {
      override def displayableName: String        = "New version saved"
      override def icon: String                   = "/assets/activities/scenarioModified.svg"
      override def supportedActions: List[String] = commentRelatedActions ::: "compare" :: Nil
    }

    case object ScenarioNameChanged extends ScenarioActivityType {
      override def displayableName: String        = "Scenario name changed"
      override def icon: String                   = "/assets/activities/scenarioModified.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object CommentAdded extends ScenarioActivityType {
      override def displayableName: String        = "Comment"
      override def icon: String                   = "/assets/activities/comment.svg"
      override def supportedActions: List[String] = commentRelatedActions
    }

    case object AttachmentAdded extends ScenarioActivityType {
      override def displayableName: String        = "Attachment"
      override def icon: String                   = "/assets/activities/attachment.svg"
      override def supportedActions: List[String] = List("download_attachment", "delete_attachment")
    }

    case object ChangedProcessingMode extends ScenarioActivityType {
      override def displayableName: String        = "Processing mode change"
      override def icon: String                   = "/assets/activities/processingModeChange.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object IncomingMigration extends ScenarioActivityType {
      override def displayableName: String        = "Incoming migration"
      override def icon: String                   = "/assets/activities/migration.svg"
      override def supportedActions: List[String] = List("compare")
    }

    case object OutgoingMigration extends ScenarioActivityType {
      override def displayableName: String        = "Outgoing migration"
      override def icon: String                   = "/assets/activities/migration.svg"
      override def supportedActions: List[String] = commentRelatedActions
    }

    case object PerformedSingleExecution extends ScenarioActivityType {
      override def displayableName: String        = "Processing data"
      override def icon: String                   = "/assets/activities/processingData.svg"
      override def supportedActions: List[String] = commentRelatedActions
    }

    case object PerformedScheduledExecution extends ScenarioActivityType {
      override def displayableName: String        = "Processing data"
      override def icon: String                   = "/assets/activities/processingData.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object AutomaticUpdate extends ScenarioActivityType {
      override def displayableName: String        = "Automatic update"
      override def icon: String                   = "/assets/activities/automaticUpdate.svg"
      override def supportedActions: List[String] = List("compare")
    }

    case object CustomAction extends ScenarioActivityType {
      override def displayableName: String        = "Custom action"
      override def icon: String                   = "/assets/activities/customAction.svg"
      override def supportedActions: List[String] = List.empty
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

    case object Deleted extends ScenarioActivityCommentContent

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
      deriveConfiguredCodec
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
      overrideDisplayableName = scenarioVersionId.map(version => s"Version $version saved"),
    )

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
      additionalFields = List.empty
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
        sourceScenarioVersionId: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.IncomingMigration,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = None,
      attachment = None,
      additionalFields = List(
        AdditionalField("sourceEnvironment", sourceEnvironment),
        AdditionalField("sourceScenarioVersionId", sourceScenarioVersionId),
      )
    )

    def forOutgoingMigration(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        comment: ScenarioActivityComment,
        destinationEnvironment: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.OutgoingMigration,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = Some(comment),
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
        dateFinished: Option[Instant],
        errorMessage: Option[String],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.PerformedSingleExecution,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = Some(comment),
      attachment = None,
      additionalFields = List(
        dateFinished.map(date => AdditionalField("dateFinished", date.toString)),
        errorMessage.map(e => AdditionalField("errorMessage", e)),
      ).flatten
    )

    def forPerformedScheduledExecution(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        dateFinished: Option[Instant],
        errorMessage: Option[String],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.PerformedScheduledExecution,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = None,
      attachment = None,
      additionalFields = List(
        dateFinished.map(date => AdditionalField("dateFinished", date.toString)),
        errorMessage.map(error => AdditionalField("errorMessage", error)),
      ).flatten
    )

    // Other/technical

    def forAutomaticUpdate(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        dateFinished: Instant,
        changes: String,
        errorMessage: Option[String],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.AutomaticUpdate,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = None,
      attachment = None,
      additionalFields = List(
        Some(AdditionalField("changes", changes)),
        Some(AdditionalField("dateFinished", dateFinished.toString)),
        errorMessage.map(e => AdditionalField("errorMessage", e)),
      ).flatten
    )

    def forCustomAction(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersionId: Option[Long],
        comment: ScenarioActivityComment,
        actionName: String,
        customIcon: Option[String],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.CustomAction,
      user = user,
      date = date,
      scenarioVersionId = scenarioVersionId,
      comment = Some(comment),
      attachment = None,
      additionalFields = List(
        AdditionalField("actionName", actionName),
      ),
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

    implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario](e => s"No scenario ${e.scenarioName} found")

    implicit val noCommentCodec: Codec[String, NoComment, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoComment](e =>
        s"Unable to delete comment with id: ${e.commentId}"
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

}
