package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.{NodeDeploymentData, NodesDeploymentData, SqlFilteringExpression}
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentId
import pl.touk.nussknacker.ui.process.repository.ApiCallComment
import sttp.model.StatusCode
import sttp.tapir.EndpointIO.{Example, Info}
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class DeploymentApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos._

  lazy val runDeploymentEndpoint: SecuredEndpoint[(DeploymentId, RunDeploymentRequest), RunDeploymentError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Run the deployment of a scenario")
      .tag("Deployments")
      .put
      .in(
        "deployments" / deploymentIdPathCapture
      )
      .in(
        jsonBody[RunDeploymentRequest]
          .example(
            RunDeploymentRequest(
              scenarioName = ProcessName("scenario1"),
              NodesDeploymentData(
                Map(NodeId("sourceNodeId1") -> SqlFilteringExpression("field1 = 'value'"))
              ),
              comment = None
            )
          )
      )
      .out(statusCode(StatusCode.Accepted))
      .errorOut(
        oneOf[RunDeploymentError](
          oneOfVariant[ConflictingDeploymentIdError](
            StatusCode.Conflict,
            plainBody[ConflictingDeploymentIdError]
              .example(
                Example.of(
                  summary = Some("Deployment with id {deploymentId} already exists"),
                  value = ConflictingDeploymentIdError(exampleDeploymentId)
                )
              )
          ),
          oneOfVariantFromMatchType(
            StatusCode.BadRequest,
            plainBody[BadRequestRunDeploymentError]
              .examples(
                List(
                  Example.of(
                    summary = Some("Scenario {scenarioName} not found"),
                    value = ScenarioNotFoundError(ProcessName("'example scenario'"))
                  ),
                  Example.of(
                    summary = Some("Comment is required"),
                    value = CommentValidationErrorNG("Comment is required.")
                  )
                )
              )
          )
        )
      )
      .withSecurity(auth)

  lazy val getDeploymentStatusEndpoint: SecuredEndpoint[DeploymentId, GetDeploymentStatusError, StatusName, Any] =
    baseNuApiEndpoint
      .summary("Get status of a deployment")
      .tag("Deployments")
      .get
      .in(
        "deployments" / deploymentIdPathCapture / "status"
      )
      .out(statusCode(StatusCode.Ok).and(stringBody))
      .errorOut(
        oneOf[GetDeploymentStatusError](
          oneOfVariantValueMatcher[DeploymentNotFoundError](
            StatusCode.NotFound,
            plainBody[DeploymentNotFoundError]
              .example(
                Example.of(
                  summary = Some("No deployment {deploymentId} found"),
                  value = DeploymentNotFoundError(exampleDeploymentId)
                )
              )
          ) {
            // MatchType macro used in oneOfVariantFromMatchType doesn't work for this case
            case DeploymentNotFoundError(_) => true
          }
        )
      )
      .withSecurity(auth)

  private lazy val exampleDeploymentId = DeploymentId(UUID.fromString("a9a1e269-0b71-4582-a948-603482d27298"))

  private lazy val deploymentIdPathCapture = path[DeploymentId]("deploymentId")
    .copy(info =
      Info
        .empty[DeploymentId]
        .description(
          "Identifier in the UUID format that will be used for the verification of deployment's status"
        )
        .example(exampleDeploymentId)
    )

}

object DeploymentApiEndpoints {

  object Dtos {

    implicit val scenarioNameSchema: Schema[ProcessName] = Schema.string[ProcessName]

    // TODO: scenario graph version / the currently active version instead of the latest
    @derive(encoder, decoder, schema)
    final case class RunDeploymentRequest(
        scenarioName: ProcessName,
        nodesDeploymentData: NodesDeploymentData,
        comment: Option[ApiCallComment]
    )

    implicit val nodeDeploymentDataCodec: Schema[NodeDeploymentData] = Schema.string[SqlFilteringExpression].as

    implicit val nodesDeploymentDataCodec: Schema[NodesDeploymentData] = Schema
      .schemaForMap[NodeId, NodeDeploymentData](_.id)
      .map[NodesDeploymentData]((map: Map[NodeId, NodeDeploymentData]) => Some(NodesDeploymentData(map)))(
        _.dataByNodeId
      )

    sealed trait RunDeploymentError

    sealed trait BadRequestRunDeploymentError extends RunDeploymentError

    final case class ConflictingDeploymentIdError(id: DeploymentId) extends RunDeploymentError

    final case class ScenarioNotFoundError(scenarioName: ProcessName) extends BadRequestRunDeploymentError

    final case class CommentValidationErrorNG(message: String) extends BadRequestRunDeploymentError

    sealed trait GetDeploymentStatusError

    final case class DeploymentNotFoundError(id: DeploymentId) extends GetDeploymentStatusError

    case object NoPermissionError extends RunDeploymentError with GetDeploymentStatusError with CustomAuthorizationError

    implicit val badRequestRunDeploymentErrorCodec: Codec[String, BadRequestRunDeploymentError, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[BadRequestRunDeploymentError] {
        case ScenarioNotFoundError(scenarioName) => s"Scenario $scenarioName not found"
        case CommentValidationErrorNG(message)   => message
      }

    implicit val conflictingDeploymentIdErrorCodec: Codec[String, ConflictingDeploymentIdError, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[ConflictingDeploymentIdError](err =>
        s"Deployment with id ${err.id} already exists"
      )

    implicit val deploymentNotFoundErrorCodec: Codec[String, DeploymentNotFoundError, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[DeploymentNotFoundError](err =>
        s"Deployment ${err.id} not found"
      )

  }

}
