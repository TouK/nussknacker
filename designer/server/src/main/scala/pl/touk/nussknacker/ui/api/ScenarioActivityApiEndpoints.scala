package pl.touk.nussknacker.ui.api

import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.{BaseEndpointDefinitions, CustomAuthorizationError}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.{
  Attachment => DbAttachment,
  Comment => DbComment,
  ProcessActivity => DbProcessActivity
}
import pl.touk.nussknacker.ui.server.HeadersSupport.FileName
import sttp.model.StatusCode.{InternalServerError, NotFound, Ok}
import sttp.model.{HeaderNames, MediaType}
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

import java.io.InputStream
import java.time.Instant

class ScenarioActivityApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import ScenarioActivityApiEndpoints.Dtos.ScenarioActivityErrors._
  import ScenarioActivityApiEndpoints.Dtos._
  import TapirCodecs.ContentDispositionCodec._
  import TapirCodecs.HeaderCodec._
  import TapirCodecs.ScenarioNameCodec._
  import TapirCodecs.VersionIdCodec._

  lazy val scenarioActivityEndpoint: SecuredEndpoint[ProcessName, ScenarioActivityErrors, ScenarioActivity, Any] =
    baseNuApiEndpoint
      .summary("Scenario activity service")
      .tag("Scenario")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity")
      .out(
        statusCode(Ok).and(
          jsonBody[ScenarioActivity].example(
            Example.of(
              summary = Some("Display scenario activity"),
              value = ScenarioActivity(
                comments = List(
                  Comment(
                    id = 1L,
                    processVersionId = 1L,
                    content = "some comment",
                    user = "test",
                    createDate = Instant.parse("2024-01-17T14:21:17Z")
                  )
                ),
                attachments = List(
                  Attachment(
                    id = 1L,
                    processVersionId = 1L,
                    fileName = "some_file.txt",
                    user = "test",
                    createDate = Instant.parse("2024-01-17T14:21:17Z")
                  )
                )
              )
            )
          )
        )
      )
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)

  lazy val addCommentEndpoint: SecuredEndpoint[AddCommentRequest, ScenarioActivityErrors, Unit, Any] =
    baseNuApiEndpoint
      .summary("Add scenario comment service")
      .tag("Scenario")
      .post
      .in(
        ("processes" / path[ProcessName]("scenarioName") / path[VersionId]("versionId") / "activity"
          / "comments" / stringBody).mapTo[AddCommentRequest]
      )
      .out(statusCode(Ok))
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)

  lazy val deleteCommentEndpoint: SecuredEndpoint[DeleteCommentRequest, ScenarioActivityErrors, Unit, Any] =
    baseNuApiEndpoint
      .summary("Delete process comment service")
      .tag("Scenario")
      .delete
      .in(
        ("processes" / path[ProcessName]("scenarioName") / "activity" / "comments"
          / path[Long]("commentId")).mapTo[DeleteCommentRequest]
      )
      .out(statusCode(Ok))
      .errorOut(
        oneOf[ScenarioActivityErrors](
          oneOfVariantFromMatchType(
            NotFound,
            plainBody[NoScenario]
              .example(
                Example.of(
                  summary = Some("No scenario {scenarioName} found"),
                  value = NoScenario(ProcessName("'example scenario'"))
                )
              )
          ),
          oneOfVariantFromMatchType(
            InternalServerError,
            plainBody[NoComment]
              .example(
                Example.of(
                  summary = Some("Unable to delete comment with id: {commentId}"),
                  value = NoComment(1L)
                )
              )
          )
        )
      )
      .withSecurity(auth)

  def addAttachmentEndpoint(
      implicit streamBodyEndpoint: EndpointInput[InputStream]
  ): SecuredEndpoint[AddAttachmentRequest, ScenarioActivityErrors, Unit, Any] = {
    baseNuApiEndpoint
      .summary("Add scenario attachment service")
      .tag("Scenario")
      .post
      .in(
        (
          "processes" / path[ProcessName]("scenarioName") / path[VersionId]("versionId") / "activity"
            / "attachments" / streamBodyEndpoint / header[FileName](HeaderNames.ContentDisposition)
        ).mapTo[AddAttachmentRequest]
      )
      .out(statusCode(Ok))
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)
  }

  def downloadAttachmentEndpoint(
      implicit streamBodyEndpoint: EndpointOutput[InputStream]
  ): SecuredEndpoint[GetAttachmentRequest, ScenarioActivityErrors, GetAttachmentResponse, Any] = {
    baseNuApiEndpoint
      .summary("Download attachment service")
      .tag("Scenario")
      .get
      .in(
        ("processes" / path[ProcessName]("processName") / "activity" / "attachments"
          / path[Long]("attachmentId")).mapTo[GetAttachmentRequest]
      )
      .out(
        statusCode(Ok)
          .and(streamBodyEndpoint)
          .and(header(HeaderNames.ContentDisposition)(optionalHeaderCodec))
          .and(header(HeaderNames.ContentType)(requiredHeaderCodec))
          .mapTo[GetAttachmentResponse]
      )
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)
  }

  private lazy val scenarioNotFoundErrorOutput: EndpointOutput.OneOf[ScenarioActivityErrors, ScenarioActivityErrors] =
    oneOf[ScenarioActivityErrors](
      oneOfVariantFromMatchType(
        NotFound,
        plainBody[NoScenario]
          .example(
            Example.of(
              summary = Some("No scenario {scenarioName} found"),
              value = NoScenario(ProcessName("'example scenario'"))
            )
          )
      )
    )

}

object ScenarioActivityApiEndpoints {

  object Dtos {
    @derive(encoder, decoder, schema)
    final case class ScenarioActivity private (comments: List[Comment], attachments: List[Attachment])

    object ScenarioActivity {

      def apply(activity: DbProcessActivity): ScenarioActivity =
        new ScenarioActivity(
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

    sealed trait ScenarioActivityErrors

    object ScenarioActivityErrors {
      case class NoScenario(scenarioName: ProcessName) extends ScenarioActivityErrors
      case object NoPermission                         extends ScenarioActivityErrors with CustomAuthorizationError
      case class NoComment(commentId: Long)            extends ScenarioActivityErrors

      implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] = {
        Codec.string.map(
          Mapping.from[String, NoScenario](_ => ???)(e => s"No scenario ${e.scenarioName} found")
        )
      }

      implicit val noCommentCodec: Codec[String, NoComment, CodecFormat.TextPlain] = {
        Codec.string.map(
          Mapping.from[String, NoComment](_ => ???)(e => s"Unable to delete comment with id: ${e.commentId}")
        )
      }

    }

  }

}
