package pl.touk.nussknacker.ui.api.description.scenarioActivity

import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.ui.api.TapirCodecs
import pl.touk.nussknacker.ui.server.HeadersSupport.FileName
import pl.touk.nussknacker.ui.server.TapirStreamEndpointProvider
import sttp.model.HeaderNames
import sttp.model.StatusCode.{InternalServerError, NotFound, Ok}
import sttp.tapir._
import sttp.tapir.docs.openapi.OpenAPIDocsOptions
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future

object Endpoints extends BaseEndpointDefinitions {

  import TapirCodecs.ContentDispositionCodec._
  import TapirCodecs.HeaderCodec._
  import TapirCodecs.ScenarioNameCodec._
  import TapirCodecs.VersionIdCodec._
  import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivityError._
  import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos._
  import pl.touk.nussknacker.ui.api.description.scenarioActivity.InputOutput._

  val apiDocumentTitle = "Nussknacker Scenario Activity API"

  val apiVersion = "1.1.0"

  def swaggerEndpoints(implicit streamProvider: TapirStreamEndpointProvider): List[ServerEndpoint[Any, Future]] =
    SwaggerInterpreter(
      swaggerUIOptions = SwaggerUIOptions.default.copy(
        pathPrefix = "api" :: "processes" :: "docs" :: Nil,
      ),
      openAPIInterpreterOptions = openAPIOptions
    ).fromEndpoints(apiEndpoints, apiDocumentTitle, apiVersion)

  def openAPIOptions: OpenAPIDocsOptions =
    OpenAPIDocsOptions.default.copy(markOptionsAsNullable = true)

  def apiEndpoints(implicit streamProvider: TapirStreamEndpointProvider): List[PublicEndpoint[_, _, _, Any]] = List(
    scenarioActivityEndpoint,
    scenarioActivitiesCountEndpoint,
    scenarioActivitiesSearchEndpoint,
    scenarioActivitiesMetadataEndpoint,
    scenarioActivitiesEndpoint,
    addCommentEndpoint,
    editCommentEndpoint,
    deleteCommentEndpoint,
    attachmentsEndpoint,
    addAttachmentEndpoint,
    downloadAttachmentEndpoint,
  )

  lazy val scenarioActivityEndpoint
      : PublicEndpoint[ProcessName, ScenarioActivityError, ScenarioCommentsAndAttachments, Any] =
    baseNuApiEndpoint
      .summary("Deprecated scenario comments and attachments service")
      .tag("Activities")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity")
      .out(
        statusCode(Ok).and(jsonBody[ScenarioCommentsAndAttachments].example(Examples.scenarioCommentsAndAttachments))
      )
      .errorOut(scenarioNotFoundErrorOutput)
      .deprecated()

  lazy val scenarioActivitiesMetadataEndpoint
      : PublicEndpoint[ProcessName, ScenarioActivityError, ScenarioActivitiesMetadata, Any] =
    baseNuApiEndpoint
      .summary("Scenario activities metadata service")
      .tag("Activities")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "activities" / "metadata")
      .out(statusCode(Ok).and(jsonBody[ScenarioActivitiesMetadata].example(ScenarioActivitiesMetadata.default)))
      .errorOut(scenarioNotFoundErrorOutput)

  lazy val scenarioActivitiesCountEndpoint: PublicEndpoint[
    (ProcessName, List[ScenarioActivityType]),
    ScenarioActivityError,
    ScenarioActivitiesCount,
    Any
  ] =
    baseNuApiEndpoint
      .summary("Scenario activities count service")
      .tag("Activities")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "activities" / "count")
      .in(activityTypeFilterInput)
      .out(statusCode(Ok).and(jsonBody[ScenarioActivitiesCount].example(ScenarioActivitiesCount(123))))
      .errorOut(scenarioNotFoundErrorOutput)

  lazy val scenarioActivitiesSearchEndpoint: PublicEndpoint[
    (ProcessName, String, List[ScenarioActivityType]),
    ScenarioActivityError,
    ScenarioActivitiesSearchResult,
    Any
  ] =
    baseNuApiEndpoint
      .summary("Scenario activities search service")
      .tag("Activities")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "activities" / "search")
      .in(searchTextInput)
      .in(activityTypeFilterInput)
      .out(
        statusCode(Ok).and(jsonBody[ScenarioActivitiesSearchResult].example(Examples.scenarioActivitiesSearchResult))
      )
      .errorOut(scenarioNotFoundErrorOutput)

  lazy val scenarioActivitiesEndpoint: PublicEndpoint[
    (ProcessName, PaginationContext, List[ScenarioActivityType]),
    ScenarioActivityError,
    ScenarioActivities,
    Any
  ] =
    baseNuApiEndpoint
      .summary("Scenario activities service")
      .tag("Activities")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "activities")
      .in(paginationContextInput)
      .in(activityTypeFilterInput)
      .out(statusCode(Ok).and(jsonBody[ScenarioActivities].example(Examples.scenarioActivities)))
      .errorOut(scenarioNotFoundErrorOutput)

  lazy val addCommentEndpoint: PublicEndpoint[AddCommentRequest, ScenarioActivityError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Add scenario comment service")
      .tag("Comments")
      .post
      .in("processes" / path[ProcessName]("scenarioName") / path[VersionId]("versionId") / "activity" / "comments")
      .in(stringBody)
      .mapInTo[AddCommentRequest]
      .out(statusCode(Ok))
      .errorOut(scenarioNotFoundErrorOutput)

  lazy val editCommentEndpoint: PublicEndpoint[EditCommentRequest, ScenarioActivityError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Edit process comment service")
      .tag("Comments")
      .put
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "comments" / path[Long]("commentId"))
      .in(stringBody)
      .mapInTo[EditCommentRequest]
      .out(statusCode(Ok))
      .errorOut(
        oneOf[ScenarioActivityError](
          oneOfVariantFromMatchType(NotFound, plainBody[NoScenario].example(Examples.noScenarioError)),
          oneOfVariantFromMatchType(InternalServerError, plainBody[NoComment].example(Examples.commentNotFoundError))
        )
      )

  lazy val deleteCommentEndpoint: PublicEndpoint[DeleteCommentRequest, ScenarioActivityError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Delete process comment service")
      .tag("Comments")
      .delete
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "comments" / path[Long]("commentId"))
      .mapInTo[DeleteCommentRequest]
      .out(statusCode(Ok))
      .errorOut(
        oneOf[ScenarioActivityError](
          oneOfVariantFromMatchType(NotFound, plainBody[NoScenario].example(Examples.noScenarioError)),
          oneOfVariantFromMatchType(InternalServerError, plainBody[NoComment].example(Examples.commentNotFoundError))
        )
      )

  val attachmentsEndpoint: PublicEndpoint[ProcessName, ScenarioActivityError, ScenarioAttachments, Any] = {
    baseNuApiEndpoint
      .summary("Scenario attachments service")
      .tag("Attachments")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "attachments")
      .out(statusCode(Ok).and(jsonBody[ScenarioAttachments].example(Examples.scenarioAttachments)))
      .errorOut(scenarioNotFoundErrorOutput)
  }

  def addAttachmentEndpoint(
      implicit streamProvider: TapirStreamEndpointProvider
  ): PublicEndpoint[AddAttachmentRequest, ScenarioActivityError, Unit, Any] = {
    baseNuApiEndpoint
      .summary("Add scenario attachment service")
      .tag("Attachments")
      .post
      .in("processes" / path[ProcessName]("scenarioName") / path[VersionId]("versionId") / "activity" / "attachments")
      .in(streamProvider.streamBodyEndpointInput)
      .in(header[FileName](HeaderNames.ContentDisposition))
      .mapInTo[AddAttachmentRequest]
      .out(statusCode(Ok))
      .errorOut(scenarioNotFoundErrorOutput)
  }

  def downloadAttachmentEndpoint(
      implicit streamProvider: TapirStreamEndpointProvider
  ): PublicEndpoint[GetAttachmentRequest, ScenarioActivityError, GetAttachmentResponse, Any] = {
    baseNuApiEndpoint
      .summary("Download attachment service")
      .tag("Attachments")
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
  }

}
