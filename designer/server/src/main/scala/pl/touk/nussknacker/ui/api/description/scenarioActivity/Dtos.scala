package pl.touk.nussknacker.ui.api.description.scenarioActivity

import derevo.circe.{decoder, encoder}
import derevo.derive
import enumeratum.Enum
import enumeratum.EnumEntry.UpperSnakecase
import io.circe
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.{
  Attachment => DbAttachment,
  Comment => DbComment,
  ProcessActivity => DbProcessActivity
}
import pl.touk.nussknacker.ui.server.HeadersSupport.FileName
import sttp.model.MediaType
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.generic.{Configuration => TapirConfiguration}

import java.io.InputStream
import java.time.Instant

object Dtos {

  final case class PaginationContext(
      pageSize: Long,
      pageNumber: Long,
  )

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
          icon = "/assets/states/error.svg"
        ),
        ScenarioActivityActionMetadata(
          id = "delete_comment",
          displayableName = "Delete",
          icon = "/assets/states/error.svg"
        ),
        ScenarioActivityActionMetadata(
          id = "edit_comment",
          displayableName = "Edit",
          icon = "/assets/states/error.svg"
        ),
        ScenarioActivityActionMetadata(
          id = "download_attachment",
          displayableName = "Download",
          icon = "/assets/states/error.svg"
        ),
        ScenarioActivityActionMetadata(
          id = "delete_attachment",
          displayableName = "Delete",
          icon = "/assets/states/error.svg"
        ),
      )
    )

  }

  sealed trait ScenarioActivityType extends UpperSnakecase {
    def displayableName: String
    def icon: String
    def additionalFields: List[String]
    def supportedActions: List[String]
    def hasAttachment: Boolean = false
  }

  object ScenarioActivityType extends Enum[ScenarioActivityType] {

    case object ScenarioCreated extends ScenarioActivityType {
      override def displayableName: String        = "Scenario created"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
      override def additionalFields: List[String] = List.empty
    }

    case object ScenarioArchived extends ScenarioActivityType {
      override def displayableName: String        = "Scenario archived"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
      override def additionalFields: List[String] = List.empty
    }

    case object ScenarioUnarchived extends ScenarioActivityType {
      override def displayableName: String        = "Scenario unarchived"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
      override def additionalFields: List[String] = List.empty
    }

    case object ScenarioDeployed extends ScenarioActivityType {
      override def displayableName: String        = "Deployment"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
      override def additionalFields: List[String] = List.empty
    }

    case object ScenarioCanceled extends ScenarioActivityType {
      override def displayableName: String        = "Cancel"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
      override def additionalFields: List[String] = List.empty
    }

    case object ScenarioModified extends ScenarioActivityType {
      override def displayableName: String        = "Version {processVersionId} saved"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List("compare")
      override def additionalFields: List[String] = List.empty
    }

    case object ScenarioNameChanged extends ScenarioActivityType {
      override def displayableName: String        = "Scenario name changed"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
      override def additionalFields: List[String] = List("oldName", "newName")
    }

    case object CommentAdded extends ScenarioActivityType {
      override def displayableName: String        = "Comment"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List("delete_comment", "edit_comment")
      override def additionalFields: List[String] = List.empty
    }

    case object CommentAddedAndDeleted extends ScenarioActivityType {
      override def displayableName: String        = "Comment"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
      override def additionalFields: List[String] = List("deletedByUser")
    }

    case object AttachmentAdded extends ScenarioActivityType {
      override def displayableName: String        = "Attachment"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
      override def additionalFields: List[String] = List.empty
    }

    case object AttachmentAddedAndDeleted extends ScenarioActivityType {
      override def displayableName: String        = "Attachment"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
      override def additionalFields: List[String] = List("deletedByUser")
    }

    case object ChangedProcessingMode extends ScenarioActivityType {
      override def displayableName: String        = "Processing mode change"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
      override def additionalFields: List[String] = List("from", "to")
    }

    case object IncomingMigration extends ScenarioActivityType {
      override def displayableName: String        = "Incoming migration"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List("compare")
      override def additionalFields: List[String] = List("sourceEnvironment", "sourceProcessVersionId")
    }

    case object OutgoingMigration extends ScenarioActivityType {
      override def displayableName: String        = "Outgoing migration"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
      override def additionalFields: List[String] = List("destinationEnvironment")
    }

    case object PerformedSingleExecution extends ScenarioActivityType {
      override def displayableName: String        = "Processing data"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
      override def additionalFields: List[String] = List("dateFinished", "status")
    }

    case object PerformedScheduledExecution extends ScenarioActivityType {
      override def displayableName: String        = "Processing data"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
      override def additionalFields: List[String] = List("params", "dateFinished", "status")
    }

    case object AutomaticUpdate extends ScenarioActivityType {
      override def displayableName: String        = "Automatic update"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List("compare")
      override def additionalFields: List[String] = List("dateFinished", "changes", "status")
    }

    override def values: IndexedSeq[ScenarioActivityType] = findValues

  }

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

  def enumSchema[T](
      items: List[T],
      encoder: T => String,
  ): Schema[T] =
    Schema.string.validate(
      Validator.enumeration(
        items,
        (i: T) => Some(encoder(i)),
      ),
    )

  @derive(encoder, decoder, schema)
  final case class ScenarioActivityMetadata(
      `type`: String,
      displayableName: String,
      icon: String,
      additionalFields: List[String],
      supportedActions: List[String],
  )

  object ScenarioActivityMetadata {

    def from(scenarioActivityType: ScenarioActivityType): ScenarioActivityMetadata =
      ScenarioActivityMetadata(
        `type` = scenarioActivityType.entryName,
        displayableName = scenarioActivityType.displayableName,
        icon = scenarioActivityType.icon,
        additionalFields = scenarioActivityType.additionalFields,
        supportedActions = scenarioActivityType.supportedActions,
      )

  }

  @derive(encoder, decoder, schema)
  final case class ScenarioActivityActionMetadata(
      id: String,
      displayableName: String,
      icon: String,
  )

  @derive(encoder, decoder, schema)
  final case class ScenarioActivities(activities: List[ScenarioActivity])

  implicit val configuration: Configuration =
    Configuration.default.withDiscriminator("type").withScreamingSnakeCaseConstructorNames

  @ConfiguredJsonCodec sealed trait ScenarioActivity {
    def user: String
    def date: Instant
    def processVersionId: Long
    def comment: Option[String]
  }

  object ScenarioActivity {

    implicit def tapirConfiguration: TapirConfiguration =
      TapirConfiguration.default.withDiscriminator("type").withScreamingSnakeCaseDiscriminatorValues

    implicit def scenarioActivitySchema: Schema[ScenarioActivity] =
      Schema.derived[ScenarioActivity]

    // Scenario lifecycle

    final case class ScenarioCreated(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
    ) extends ScenarioActivity

    final case class ScenarioArchived(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
    ) extends ScenarioActivity

    final case class ScenarioUnarchived(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
    ) extends ScenarioActivity

    // Scenario deployments

    final case class ScenarioDeployed(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
    ) extends ScenarioActivity

    final case class ScenarioCanceled(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
    ) extends ScenarioActivity

    // Scenario modifications

    final case class ScenarioModified(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
    ) extends ScenarioActivity

    final case class ScenarioNameChanged(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
        oldName: String,
        newName: String,
    ) extends ScenarioActivity

    final case class CommentAdded(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
    ) extends ScenarioActivity

    final case class CommentAddedAndDeleted(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
        deletedByUser: String,
    ) extends ScenarioActivity

    final case class AttachmentAdded(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
        attachment: Attachment,
    ) extends ScenarioActivity

    final case class AttachmentAddedAndDeleted(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
        deletedByUser: String,
    ) extends ScenarioActivity

    final case class ChangedProcessingMode(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
        from: String,
        to: String,
    ) extends ScenarioActivity

    // Migration between environments

    final case class IncomingMigration(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
        sourceEnvironment: String,
        sourceProcessVersionId: String,
    ) extends ScenarioActivity

    final case class OutgoingMigration(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
        destinationEnvironment: String,
    ) extends ScenarioActivity

    // Batch

    final case class PerformedSingleExecution(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
        dateFinished: String,
        status: String,
    ) extends ScenarioActivity

    final case class PerformedScheduledExecution(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
        dateFinished: String,
        params: String,
        status: String,
    ) extends ScenarioActivity

    // Other/technical

    final case class AutomaticUpdate(
        user: String,
        date: Instant,
        processVersionId: Long,
        comment: Option[String],
        dateFinished: String,
        changes: String,
        status: String,
    ) extends ScenarioActivity

  }

  @derive(encoder, decoder, schema)
  final case class ScenarioCommentsAndAttachments private (comments: List[Comment], attachments: List[Attachment])

  object ScenarioCommentsAndAttachments {

    def apply(activity: DbProcessActivity): ScenarioCommentsAndAttachments =
      new ScenarioCommentsAndAttachments(
        comments = activity.comments.map(Comment.apply),
        attachments = activity.attachments.map(Attachment.apply)
      )

  }

  @derive(encoder, decoder, schema)
  final case class Comment private (
      id: Long,
      processVersionId: Long,
      content: String,
      user: String,
      createDate: Instant
  )

  object Comment {

    def apply(comment: DbComment): Comment =
      new Comment(
        id = comment.id,
        processVersionId = comment.processVersionId.value,
        content = comment.content,
        user = comment.user,
        createDate = comment.createDate
      )

  }

  @derive(encoder, decoder, schema)
  final case class Attachment private (
      id: Long,
      processVersionId: Long,
      fileName: String,
      user: String,
      createDate: Instant
  )

  object Attachment {

    def apply(attachment: DbAttachment): Attachment =
      new Attachment(
        id = attachment.id,
        processVersionId = attachment.processVersionId.value,
        fileName = attachment.fileName,
        user = attachment.user,
        createDate = attachment.createDate
      )

  }

  final case class AddCommentRequest(scenarioName: ProcessName, versionId: VersionId, commentContent: String)

  final case class DeleteCommentRequest(scenarioName: ProcessName, commentId: Long)

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
    final case class NoComment(commentId: Long)            extends ScenarioActivityError

    implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario](e => s"No scenario ${e.scenarioName} found")

    implicit val noCommentCodec: Codec[String, NoComment, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoComment](e =>
        s"Unable to delete comment with id: ${e.commentId}"
      )

  }

}
