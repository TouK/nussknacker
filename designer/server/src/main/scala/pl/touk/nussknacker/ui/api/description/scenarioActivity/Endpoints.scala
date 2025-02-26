package pl.touk.nussknacker.ui.api.description.scenarioActivity

import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.TapirCodecs
import pl.touk.nussknacker.ui.server.HeadersSupport.FileName
import pl.touk.nussknacker.ui.server.TapirStreamEndpointProvider
import sttp.model.HeaderNames
import sttp.model.StatusCode._
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import java.time.ZoneId
import java.util.UUID

class Endpoints(auth: EndpointInput[AuthCredentials], streamProvider: TapirStreamEndpointProvider, zoneId: ZoneId)
    extends BaseEndpointDefinitions {

  import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos._
  import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivityError._
  import pl.touk.nussknacker.ui.api.description.scenarioActivity.InputOutput._

  import TapirCodecs.ContentDispositionCodec._
  import TapirCodecs.HeaderCodec._
  import TapirCodecs.ScenarioNameCodec._
  import TapirCodecs.VersionIdCodec._

  lazy val deprecatedScenarioActivityEndpoint
      : SecuredEndpoint[ProcessName, ScenarioActivityError, Legacy.ProcessActivity, Any] =
    baseNuApiEndpoint
      .summary("Scenario activity")
      .tag("Activities")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity")
      .out(statusCode(Ok).and(jsonBody[Legacy.ProcessActivity].example(Examples.deprecatedScenarioActivity)))
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)
      .deprecated()

  lazy val deprecatedAddCommentEndpoint: SecuredEndpoint[AddCommentRequest, ScenarioActivityError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Add scenario comment")
      .tag("Activities")
      .post
      .in("processes" / path[ProcessName]("scenarioName") / path[VersionId]("versionId") / "activity" / "comments")
      .in(stringBody)
      .mapInTo[AddCommentRequest]
      .out(statusCode(Ok))
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)
      .deprecated()

  lazy val deprecatedDeleteCommentEndpoint
      : SecuredEndpoint[DeprecatedDeleteCommentRequest, ScenarioActivityError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Delete process comment")
      .tag("Activities")
      .delete
      .in(
        "processes" / path[ProcessName]("scenarioName") / "activity" / "comments" / path[Long]("commentId")
      )
      .mapInTo[DeprecatedDeleteCommentRequest]
      .out(statusCode(Ok))
      .errorOut(
        oneOf[ScenarioActivityError](
          oneOfVariant(BadRequest, plainBody[InvalidComment].example(Examples.invalidCommentError)),
          oneOfVariant(NotFound, plainBody[NoScenario].example(Examples.noScenarioError)),
          oneOfVariant(InternalServerError, plainBody[NoComment].example(Examples.commentNotFoundError)),
          oneOfVariant(InternalServerError, plainBody[NoActivity].example(Examples.activityNotFoundError)),
        )
      )
      .withSecurity(auth)
      .deprecated()

  lazy val scenarioActivitiesEndpoint: SecuredEndpoint[
    ProcessName,
    ScenarioActivityError,
    ScenarioActivities,
    Any
  ] =
    baseNuApiEndpoint
      .summary("Scenario activities")
      .tag("Activities")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "activities")
      .out(statusCode(Ok).and(jsonBody[ScenarioActivities].example(Examples.scenarioActivities(zoneId))))
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)

  lazy val scenarioActivitiesMetadataEndpoint
      : SecuredEndpoint[ProcessName, ScenarioActivityError, ScenarioActivitiesMetadata, Any] =
    baseNuApiEndpoint
      .summary("Scenario activities metadata")
      .tag("Activities")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "activities" / "metadata")
      .out(
        statusCode(Ok).and(
          jsonBody[ScenarioActivitiesMetadata]
            .example(ScenarioActivitiesMetadata.default(ScenarioType.Scenario))
            .example(ScenarioActivitiesMetadata.default(ScenarioType.Fragment))
        )
      )
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)

  lazy val addCommentEndpoint: SecuredEndpoint[AddCommentRequest, ScenarioActivityError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Add scenario comment")
      .tag("Activities")
      .post
      .in("processes" / path[ProcessName]("scenarioName") / path[VersionId]("versionId") / "activity" / "comment")
      .in(stringBody)
      .mapInTo[AddCommentRequest]
      .out(statusCode(Ok))
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)

  lazy val editCommentEndpoint: SecuredEndpoint[EditCommentRequest, ScenarioActivityError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Edit process comment")
      .tag("Activities")
      .put
      .in(
        "processes" / path[ProcessName]("scenarioName") / "activity" / "comment" / path[UUID]("scenarioActivityId")
      )
      .in(stringBody)
      .mapInTo[EditCommentRequest]
      .out(statusCode(Ok))
      .errorOut(
        oneOf[ScenarioActivityError](
          oneOfVariant(BadRequest, plainBody[InvalidComment].example(Examples.invalidCommentError)),
          oneOfVariant(NotFound, plainBody[NoScenario].example(Examples.noScenarioError)),
          oneOfVariant(InternalServerError, plainBody[NoComment].example(Examples.commentNotFoundError)),
          oneOfVariant(InternalServerError, plainBody[NoActivity].example(Examples.activityNotFoundError)),
        )
      )
      .withSecurity(auth)

  lazy val deleteCommentEndpoint: SecuredEndpoint[DeleteCommentRequest, ScenarioActivityError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Delete process comment")
      .tag("Activities")
      .delete
      .in(
        "processes" / path[ProcessName]("scenarioName") / "activity" / "comment" / path[UUID]("scenarioActivityId")
      )
      .mapInTo[DeleteCommentRequest]
      .out(statusCode(Ok))
      .errorOut(
        oneOf[ScenarioActivityError](
          oneOfVariant(BadRequest, plainBody[InvalidComment].example(Examples.invalidCommentError)),
          oneOfVariant(NotFound, plainBody[NoScenario].example(Examples.noScenarioError)),
          oneOfVariant(InternalServerError, plainBody[NoComment].example(Examples.commentNotFoundError)),
          oneOfVariant(InternalServerError, plainBody[NoActivity].example(Examples.activityNotFoundError)),
        )
      )
      .withSecurity(auth)

  val attachmentsEndpoint: SecuredEndpoint[ProcessName, ScenarioActivityError, ScenarioAttachments, Any] = {
    baseNuApiEndpoint
      .summary("Scenario attachments")
      .tag("Activities")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "attachments")
      .out(statusCode(Ok).and(jsonBody[ScenarioAttachments].example(Examples.scenarioAttachments)))
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)
  }

  val addAttachmentEndpoint: SecuredEndpoint[AddAttachmentRequest, ScenarioActivityError, Unit, Any] = {
    baseNuApiEndpoint
      .summary("Add scenario attachment")
      .tag("Activities")
      .post
      .in("processes" / path[ProcessName]("scenarioName") / path[VersionId]("versionId") / "activity" / "attachments")
      .in(streamProvider.streamBodyEndpointInput)
      .in(header[FileName](HeaderNames.ContentDisposition))
      .mapInTo[AddAttachmentRequest]
      .out(statusCode(Ok))
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)
  }

  val deleteAttachmentEndpoint: SecuredEndpoint[DeleteAttachmentRequest, ScenarioActivityError, Unit, Any] = {
    baseNuApiEndpoint
      .summary("Delete scenario attachment")
      .tag("Activities")
      .delete
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "attachments" / path[Long]("attachmentId"))
      .mapInTo[DeleteAttachmentRequest]
      .out(statusCode(Ok))
      .errorOut(
        oneOf[ScenarioActivityError](
          oneOfVariant(NotFound, plainBody[NoScenario].example(Examples.noScenarioError)),
          oneOfVariant(InternalServerError, plainBody[NoAttachment].example(Examples.attachmentNotFoundError)),
        )
      )
      .withSecurity(auth)
  }

  val downloadAttachmentEndpoint
      : SecuredEndpoint[GetAttachmentRequest, ScenarioActivityError, GetAttachmentResponse, Any] = {
    baseNuApiEndpoint
      .summary("Download attachment")
      .tag("Activities")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "attachments" / path[Long]("attachmentId"))
      .mapInTo[GetAttachmentRequest]
      .out(
        statusCode(Ok)
          .and(streamProvider.streamBodyEndpointOutput)
          .and(header(HeaderNames.ContentDisposition)(optionalHeaderCodec))
          .and(header(HeaderNames.ContentType)(requiredHeaderCodec))
          .mapTo[GetAttachmentResponse]
      )
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)
  }

}
