package pl.touk.nussknacker.ui.api.description.scenarioActivity

import derevo.circe.{decoder, encoder}
import derevo.derive
import enumeratum.EnumEntry.UpperSnakecase
import enumeratum.{Enum, EnumEntry}
import io.circe
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivity.AdditionalField
import pl.touk.nussknacker.ui.server.HeadersSupport.FileName
import sttp.model.MediaType
import sttp.tapir._
import sttp.tapir.derevo.schema

import java.io.InputStream
import java.time.Instant
import java.util.UUID
import scala.collection.immutable

object Dtos {

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
  final case class ScenarioActivities(activities: List[ScenarioActivity])

  implicit val configuration: Configuration =
    Configuration.default.withDiscriminator("type").withScreamingSnakeCaseConstructorNames

  @derive(schema)
  final case class ScenarioActivity(
      id: String,
      `type`: ScenarioActivityType,
      user: String,
      date: Instant,
      scenarioVersion: Option[Long],
      comment: Option[String],
      additionalFields: List[AdditionalField],
      overrideDisplayableName: Option[String] = None,
      overrideSupportedActions: Option[List[String]] = None
  )

  object ScenarioActivity {

    implicit val scenarioActivityCodec: circe.Codec[ScenarioActivity] = {
      implicit val configuration: Configuration =
        Configuration.default.withDiscriminator("type").withScreamingSnakeCaseConstructorNames
      deriveConfiguredCodec
    }

    @derive(encoder, decoder, schema)
    final case class AdditionalField(
        name: String,
        value: String
    )

    def forScenarioCreated(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioCreated,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List.empty,
    )

    def forScenarioArchived(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioArchived,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List.empty,
    )

    def forScenarioUnarchived(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioUnarchived,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List.empty,
    )

    // Scenario deployments

    def forScenarioDeployed(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioDeployed,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List.empty,
    )

    def forScenarioCanceled(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioCanceled,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List.empty,
    )

    // Scenario modifications

    def forScenarioModified(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioModified,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List.empty,
      overrideDisplayableName = Some(s"Version $scenarioVersion saved"),
    )

    def forScenarioNameChanged(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
        oldName: String,
        newName: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ScenarioNameChanged,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List(
        AdditionalField("oldName", oldName),
        AdditionalField("newName", newName),
      )
    )

    def forCommentAdded(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.CommentAdded,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List.empty,
    )

    def forCommentAddedAndDeleted(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
        deletedByUser: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.CommentAdded,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List(
        AdditionalField("deletedByUser", deletedByUser),
      ),
      overrideSupportedActions = Some(List.empty)
    )

    def forAttachmentPresent(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
        attachmentId: String,
        attachmentFilename: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.AttachmentAdded,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List(
        AdditionalField("attachmentId", attachmentId),
        AdditionalField("attachmentFilename", attachmentFilename),
      )
    )

    def forAttachmentDeleted(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
        deletedByUser: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.AttachmentAdded,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List(
        AdditionalField("deletedByUser", deletedByUser),
      ),
      overrideSupportedActions = Some(List.empty)
    )

    def forChangedProcessingMode(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
        from: String,
        to: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.ChangedProcessingMode,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List(
        AdditionalField("from", from),
        AdditionalField("to", to),
      )
    )

    // Migration between environments

    def forIncomingMigration(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
        sourceEnvironment: String,
        sourceScenarioVersion: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.IncomingMigration,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List(
        AdditionalField("sourceEnvironment", sourceEnvironment),
        AdditionalField("sourceScenarioVersion", sourceScenarioVersion),
      )
    )

    def forOutgoingMigration(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
        destinationEnvironment: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.OutgoingMigration,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List(
        AdditionalField("destinationEnvironment", destinationEnvironment),
      )
    )

    // Batch

    def forPerformedSingleExecution(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
        dateFinished: String,
        status: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.PerformedSingleExecution,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List(
        AdditionalField("dateFinished", dateFinished),
        AdditionalField("status", status),
      )
    )

    def forPerformedScheduledExecution(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
        dateFinished: String,
        params: String,
        status: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.PerformedScheduledExecution,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List(
        AdditionalField("params", params),
        AdditionalField("dateFinished", dateFinished),
        AdditionalField("status", status),
      )
    )

    // Other/technical

    def forAutomaticUpdate(
        id: String,
        user: String,
        date: Instant,
        scenarioVersion: Long,
        comment: Option[String],
        dateFinished: String,
        changes: String,
        status: String,
    ): ScenarioActivity = ScenarioActivity(
      id = id,
      `type` = ScenarioActivityType.AutomaticUpdate,
      user = user,
      date = date,
      scenarioVersion = Some(scenarioVersion),
      comment = comment,
      additionalFields = List(
        AdditionalField("changes", changes),
        AdditionalField("dateFinished", dateFinished),
        AdditionalField("status", status),
      )
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

  final case class EditCommentRequest(
      scenarioName: ProcessName,
      scenarioActivityId: UUID,
      commentContent: String
  )

  final case class DeleteCommentRequest(scenarioName: ProcessName, scenarioActivityId: UUID)

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
    final case class NoComment(scenarioActivityId: String) extends ScenarioActivityError

    implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario](e => s"No scenario ${e.scenarioName} found")

    implicit val noCommentCodec: Codec[String, NoComment, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoComment](e =>
        s"Unable to delete comment for activity with id: ${e.scenarioActivityId}"
      )

  }

}
