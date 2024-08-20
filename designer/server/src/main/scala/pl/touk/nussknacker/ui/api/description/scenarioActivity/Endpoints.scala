package pl.touk.nussknacker.ui.api.description.scenarioActivity

import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.ui.api.TapirCodecs
import pl.touk.nussknacker.ui.server.HeadersSupport.FileName
import pl.touk.nussknacker.ui.server.TapirStreamEndpointProvider
import sttp.model.HeaderNames
import sttp.model.StatusCode.{InternalServerError, NotFound, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.docs.openapi.OpenAPIDocsOptions
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.SwaggerUIOptions
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import java.time.Instant
import scala.concurrent.Future

object Endpoints extends BaseEndpointDefinitions {

  import TapirCodecs.ContentDispositionCodec._
  import TapirCodecs.HeaderCodec._
  import TapirCodecs.ScenarioNameCodec._
  import TapirCodecs.VersionIdCodec._
  import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivityError._
  import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos._

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
    scenarioActivitiesMetadataEndpoint,
    scenarioActivitiesEndpoint,
    addCommentEndpoint,
    deleteCommentEndpoint,
    addAttachmentEndpoint,
    downloadAttachmentEndpoint,
  )

  lazy val scenarioActivityEndpoint
      : PublicEndpoint[ProcessName, ScenarioActivityError, ScenarioCommentsAndAttachments, Any] =
    baseNuApiEndpoint
      .summary("Scenario activity service")
      .tag("Scenario")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity")
      .out(
        statusCode(Ok).and(
          jsonBody[ScenarioCommentsAndAttachments].example(
            Example.of(
              summary = Some("Display scenario activity"),
              value = ScenarioCommentsAndAttachments(
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
      .deprecated()

  lazy val scenarioActivitiesMetadataEndpoint
      : PublicEndpoint[ProcessName, ScenarioActivityError, ScenarioActivitiesMetadata, Any] =
    baseNuApiEndpoint
      .summary("Scenario activities metadata service")
      .tag("Scenario")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "activities" / "metadata")
      .out(statusCode(Ok).and(jsonBody[ScenarioActivitiesMetadata].example(ScenarioActivitiesMetadata.default)))
      .errorOut(scenarioNotFoundErrorOutput)

  lazy val scenarioActivitiesEndpoint
      : PublicEndpoint[(ProcessName, PaginationContext), ScenarioActivityError, ScenarioActivities, Any] =
    baseNuApiEndpoint
      .summary("Scenario activities service")
      .tag("Scenario")
      .get
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "activities")
      .in(paginationContextInput)
      .out(
        statusCode(Ok).and(
          jsonBody[ScenarioActivities].example(
            Example.of(
              summary = Some("Display scenario actions"),
              value = ScenarioActivities(
                activities = List(
                  ScenarioActivity.ScenarioCreated(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = None,
                  ),
                  ScenarioActivity.ScenarioArchived(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = None,
                  ),
                  ScenarioActivity.ScenarioUnarchived(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = None,
                  ),
                  ScenarioActivity.ScenarioDeployed(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = Some("Deployment of scenario - task JIRA-1234"),
                  ),
                  ScenarioActivity.ScenarioCanceled(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = Some("Canceled because marketing campaign ended"),
                  ),
                  ScenarioActivity.ScenarioModified(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = Some("Added new processing step"),
                  ),
                  ScenarioActivity.ScenarioNameChanged(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = None,
                    oldName = "marketing campaign",
                    newName = "old marketing campaign",
                  ),
                  ScenarioActivity.CommentAdded(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = Some("Now scenario handles errors in datasource better"),
                  ),
                  ScenarioActivity.CommentAddedAndDeleted(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = None,
                    deletedByUser = "John Doe",
                  ),
                  ScenarioActivity.AttachmentAdded(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = None,
                    attachment = Attachment(
                      id = 10000001,
                      processVersionId = 1,
                      fileName = "attachment01.png",
                      user = "John Doe",
                      createDate = Instant.parse("2024-01-17T14:21:17Z"),
                    ),
                  ),
                  ScenarioActivity.AttachmentAddedAndDeleted(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = None,
                    deletedByUser = "John Doe",
                  ),
                  ScenarioActivity.ChangedProcessingMode(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = None,
                    from = "Request-Response",
                    to = "Batch",
                  ),
                  ScenarioActivity.IncomingMigration(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = Some("Migration from preprod"),
                    sourceEnvironment = "preprod",
                    sourceProcessVersionId = "23",
                  ),
                  ScenarioActivity.OutgoingMigration(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = Some("Migration to preprod"),
                    destinationEnvironment = "preprod",
                  ),
                  ScenarioActivity.PerformedSingleExecution(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    comment = None,
                    dateFinished = "2024-01-17T14:21:17Z",
                    status = "Successfully executed",
                  ),
                  ScenarioActivity.PerformedScheduledExecution(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    dateFinished = "2024-01-17T14:21:17Z",
                    comment = None,
                    params = "Batch size=1",
                    status = "Successfully executed",
                  ),
                  ScenarioActivity.AutomaticUpdate(
                    user = "some user",
                    date = Instant.parse("2024-01-17T14:21:17Z"),
                    processVersionId = 1,
                    dateFinished = "2024-01-17T14:21:17Z",
                    comment = None,
                    changes = "JIRA-12345, JIRA-32146",
                    status = "Successful",
                  ),
                ),
              )
            )
          )
        )
      )
      .errorOut(scenarioNotFoundErrorOutput)

  lazy val addCommentEndpoint: PublicEndpoint[AddCommentRequest, ScenarioActivityError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Add scenario comment service")
      .tag("Scenario")
      .post
      .in("processes" / path[ProcessName]("scenarioName") / path[VersionId]("versionId") / "activity" / "comments")
      .in(stringBody)
      .mapInTo[AddCommentRequest]
      .out(statusCode(Ok))
      .errorOut(scenarioNotFoundErrorOutput)

  lazy val deleteCommentEndpoint: PublicEndpoint[DeleteCommentRequest, ScenarioActivityError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Delete process comment service")
      .tag("Scenario")
      .delete
      .in("processes" / path[ProcessName]("scenarioName") / "activity" / "comments" / path[Long]("commentId"))
      .mapInTo[DeleteCommentRequest]
      .out(statusCode(Ok))
      .errorOut(
        oneOf[ScenarioActivityError](
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

  def addAttachmentEndpoint(
      implicit streamProvider: TapirStreamEndpointProvider
  ): PublicEndpoint[AddAttachmentRequest, ScenarioActivityError, Unit, Any] = {
    baseNuApiEndpoint
      .summary("Add scenario attachment service")
      .tag("Scenario")
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
      .tag("Scenario")
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

  private lazy val scenarioNotFoundErrorOutput: EndpointOutput.OneOf[ScenarioActivityError, ScenarioActivityError] =
    oneOf[ScenarioActivityError](
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

  private val paginationContextInput: EndpointInput[PaginationContext] =
    query[Long]("pageSize")
      .and(query[Long]("pageNumber"))
      .map(PaginationContext.tupled.apply(_))(PaginationContext.unapply(_).get)

}
