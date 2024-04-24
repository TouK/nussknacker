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
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.RunDeploymentRequest
import pl.touk.nussknacker.ui.error._
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentIdNG
import pl.touk.nussknacker.ui.process.repository.ApiCallComment
import sttp.model.StatusCode
import sttp.tapir.EndpointIO.{Example, Info}
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class DeploymentApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  private val exampleDeploymentId = DeploymentIdNG(UUID.fromString("a9a1e269-0b71-4582-a948-603482d27298"))

  private val deploymentIdPathCapture = path[DeploymentIdNG]("deploymentId")
    .copy(info =
      Info
        .empty[DeploymentIdNG]
        .description(
          "Identifier in the UUID format that will be used for the verification of deployment's status"
        )
        .example(exampleDeploymentId)
    )

  lazy val runDeploymentEndpoint
      : SecuredEndpoint[(DeploymentIdNG, RunDeploymentRequest), RunDeploymentError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Run the deployment of a scenario")
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
          oneOfVariantFromMatchType(
            StatusCode.BadRequest,
            plainBody[ScenarioNotFoundError]
              .example(
                Example.of(
                  summary = Some("Scenario {scenarioName} not found"),
                  value = ScenarioNotFoundError(ProcessName("'example scenario'"))
                )
              )
          ),
          oneOfVariantFromMatchType(
            StatusCode.BadRequest,
            plainBody[CommentValidationErrorNG]
              .example(
                Example.of(
                  summary = Some("Comment is required"),
                  value = CommentValidationErrorNG("Comment is required.")
                )
              )
          )
        )
      )
      .withSecurity(auth)

  lazy val getDeploymentStatusEndpoint: SecuredEndpoint[DeploymentIdNG, GetDeploymentStatusError, StatusName, Any] =
    baseNuApiEndpoint
      .summary("Get status of a deployment")
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

  }

}
