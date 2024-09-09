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

    case object ScenarioCreated extends ScenarioActivityType {
      override def displayableName: String        = "Scenario created"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object ScenarioArchived extends ScenarioActivityType {
      override def displayableName: String        = "Scenario archived"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object ScenarioUnarchived extends ScenarioActivityType {
      override def displayableName: String        = "Scenario unarchived"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object ScenarioDeployed extends ScenarioActivityType {
      override def displayableName: String        = "Deployment"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object ScenarioPaused extends ScenarioActivityType {
      override def displayableName: String        = "Pause"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object ScenarioCanceled extends ScenarioActivityType {
      override def displayableName: String        = "Cancel"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object ScenarioModified extends ScenarioActivityType {
      override def displayableName: String        = "New version saved"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List("compare")
    }

    case object ScenarioNameChanged extends ScenarioActivityType {
      override def displayableName: String        = "Scenario name changed"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object CommentAdded extends ScenarioActivityType {
      override def displayableName: String        = "Comment"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List("delete_comment", "edit_comment")
    }

    case object AttachmentAdded extends ScenarioActivityType {
      override def displayableName: String        = "Attachment"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object ChangedProcessingMode extends ScenarioActivityType {
      override def displayableName: String        = "Processing mode change"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object IncomingMigration extends ScenarioActivityType {
      override def displayableName: String        = "Incoming migration"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List("compare")
    }

    case object OutgoingMigration extends ScenarioActivityType {
      override def displayableName: String        = "Outgoing migration"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object PerformedSingleExecution extends ScenarioActivityType {
      override def displayableName: String        = "Processing data"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object PerformedScheduledExecution extends ScenarioActivityType {
      override def displayableName: String        = "Processing data"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List.empty
    }

    case object AutomaticUpdate extends ScenarioActivityType {
      override def displayableName: String        = "Automatic update"
      override def icon: String                   = "/assets/states/error.svg"
      override def supportedActions: List[String] = List("compare")
    }

    case object CustomAction extends ScenarioActivityType {
      override def displayableName: String        = "Custom action"
      override def icon: String                   = "/assets/states/error.svg"
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
  final case class ScenarioActivityComment(comment: Option[String], lastModifiedBy: String, lastModifiedAt: Instant)

  @derive(encoder, decoder, schema)
  final case class ScenarioActivityAttachment(
      id: Option[Long],
      filename: String,
      lastModifiedBy: String,
      lastModifiedAt: Instant
  )

  @derive(encoder, decoder, schema)
  final case class ScenarioActivities(activities: List[ScenarioActivity])

  sealed trait ScenarioActivity {
    def id: UUID
    def user: String
    def date: Instant
    def scenarioVersion: Option[Long]
  }

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

    final case class ScenarioCreated(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
    ) extends ScenarioActivity

    final case class ScenarioArchived(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
    ) extends ScenarioActivity

    final case class ScenarioUnarchived(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
    ) extends ScenarioActivity

    // Scenario deployments

    final case class ScenarioDeployed(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
        comment: ScenarioActivityComment,
    ) extends ScenarioActivity

    final case class ScenarioPaused(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
        comment: ScenarioActivityComment,
    ) extends ScenarioActivity

    final case class ScenarioCanceled(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
        comment: ScenarioActivityComment,
    ) extends ScenarioActivity

    // Scenario modifications

    final case class ScenarioModified(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
        comment: ScenarioActivityComment,
    ) extends ScenarioActivity

    final case class ScenarioNameChanged(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
        oldName: String,
        newName: String,
    ) extends ScenarioActivity

    final case class CommentAdded(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
        comment: ScenarioActivityComment,
    ) extends ScenarioActivity

    final case class AttachmentAdded(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
        attachment: ScenarioActivityAttachment,
    ) extends ScenarioActivity

    final case class ChangedProcessingMode(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
        from: String,
        to: String,
    ) extends ScenarioActivity

    // Migration between environments

    final case class IncomingMigration(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
        sourceEnvironment: String,
        sourceScenarioVersion: String,
    ) extends ScenarioActivity

    final case class OutgoingMigration(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
        comment: ScenarioActivityComment,
        destinationEnvironment: String,
    ) extends ScenarioActivity

    // Batch

    final case class PerformedSingleExecution(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
        dateFinished: Instant,
        errorMessage: Option[String],
    ) extends ScenarioActivity

    final case class PerformedScheduledExecution(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
        dateFinished: Instant,
        errorMessage: Option[String],
    ) extends ScenarioActivity

    // Other/technical

    final case class AutomaticUpdate(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
        dateFinished: Instant,
        changes: String,
        errorMessage: Option[String],
    ) extends ScenarioActivity

    final case class CustomAction(
        id: UUID,
        user: String,
        date: Instant,
        scenarioVersion: Option[Long],
        actionName: String,
    ) extends ScenarioActivity

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

    // todo NU-1772 - remove this error when API is implemented
    final case object NotImplemented extends ScenarioActivityError

    final case class NoScenario(scenarioName: ProcessName) extends ScenarioActivityError
    final case object NoPermission                         extends ScenarioActivityError with CustomAuthorizationError
    final case class NoComment(commentId: String)          extends ScenarioActivityError

    implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario](e => s"No scenario ${e.scenarioName} found")

    implicit val noCommentCodec: Codec[String, NoComment, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoComment](e =>
        s"Unable to delete comment with id: ${e.commentId}"
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
