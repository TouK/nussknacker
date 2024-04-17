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
import pl.touk.nussknacker.ui.api.TapirCodecs
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.DeploymentError.{
  DeploymentCommentError,
  NoScenario
}
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.{
  DeploymentError,
  DeploymentRequest,
  RequestedDeploymentId
}
import pl.touk.nussknacker.ui.process.repository.ApiCallComment
import sttp.model.StatusCode
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.EndpointIO.{Example, Info}
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class DeploymentApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import TapirCodecs.ScenarioNameCodec._

  private val deploymentIdPathCapture = path[RequestedDeploymentId]("deploymentId")
    .copy(info =
      Info
        .empty[RequestedDeploymentId]
        .description(
          "Identifier in the UUID format that will be used for the verification of deployment's status"
        )
        .example(RequestedDeploymentId(UUID.fromString("a9a1e269-0b71-4582-a948-603482d27298")))
    )

  lazy val requestScenarioDeploymentEndpoint
      : SecuredEndpoint[(ProcessName, RequestedDeploymentId, DeploymentRequest), DeploymentError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Service allowing to request the deployment of a scenario")
      .put
      .in(
        "scenarios" / path[ProcessName]("scenarioName") / "deployments" / deploymentIdPathCapture
      )
      .in(
        jsonBody[DeploymentRequest]
          .example(
            DeploymentRequest(
              NodesDeploymentData(
                Map(NodeId("sourceNodeId1") -> SqlFilteringExpression("field1 = 'value'"))
              ),
              comment = None
            )
          )
      )
      .out(statusCode(StatusCode.Accepted))
      .errorOut(
        oneOf[DeploymentError](
          oneOfVariantFromMatchType(
            StatusCode.NotFound,
            plainBody[NoScenario]
              .example(
                Example.of(
                  summary = Some("No scenario {scenarioName} found"),
                  value = NoScenario(ProcessName("'example scenario'"))
                )
              )
          ),
          oneOfVariantFromMatchType(
            StatusCode.BadRequest,
            plainBody[DeploymentCommentError]
              .example(
                Example.of(
                  summary = Some("Comment is required"),
                  value = DeploymentCommentError("Comment is required.")
                )
              )
          )
        )
      )
      .withSecurity(auth)

  lazy val checkDeploymentStatusEndpoint
      : SecuredEndpoint[(ProcessName, RequestedDeploymentId), DeploymentError, StatusName, Any] =
    baseNuApiEndpoint
      .summary("Service allowing to check status of deployment")
      .get
      .in(
        "scenarios" / path[ProcessName]("scenarioName") / "deployments" / deploymentIdPathCapture / "status"
      )
      .out(statusCode(StatusCode.Ok).and(stringBody))
      .errorOut(
        oneOf[DeploymentError](
          oneOfVariantFromMatchType(
            StatusCode.NotFound,
            plainBody[NoScenario]
              .example(
                Example.of(
                  summary = Some("No scenario {scenarioName} found"),
                  value = NoScenario(ProcessName("'example scenario'"))
                )
              )
          )
        )
      )
      .withSecurity(auth)

}

object DeploymentApiEndpoints {

  object Dtos {

    // TODO: scenario graph version / the currently active version instead of the latest
    @derive(encoder, decoder, schema)
    final case class DeploymentRequest(
        nodesDeploymentData: NodesDeploymentData,
        comment: Option[ApiCallComment]
    )

    implicit val nodeDeploymentDataCodec: Schema[NodeDeploymentData] = Schema.string[SqlFilteringExpression].as

    implicit val nodesDeploymentDataCodec: Schema[NodesDeploymentData] = Schema
      .schemaForMap[NodeId, NodeDeploymentData](_.id)
      .map[NodesDeploymentData]((map: Map[NodeId, NodeDeploymentData]) => Some(NodesDeploymentData(map)))(
        _.dataByNodeId
      )

    sealed trait DeploymentError

    object DeploymentError {

      final case class NoScenario(scenarioName: ProcessName) extends DeploymentError

      final case object NoPermission extends DeploymentError with CustomAuthorizationError

      final case class DeploymentCommentError(message: String) extends DeploymentError

      private def deserializationNotSupportedException =
        (ignored: Any) => throw new IllegalStateException("Deserializing errors is not supported.")

      implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] = {
        Codec.string.map(
          Mapping.from[String, NoScenario](deserializationNotSupportedException)(e =>
            s"No scenario ${e.scenarioName} found"
          )
        )
      }

      implicit val deploymentCommentErrorCodec: Codec[String, DeploymentCommentError, CodecFormat.TextPlain] = {
        Codec.string.map(
          Mapping.from[String, DeploymentCommentError](deserializationNotSupportedException)(_.message)
        )
      }

    }

    final case class RequestedDeploymentId(value: UUID) {
      override def toString: String = value.toString
    }

    object RequestedDeploymentId {

      def generate: RequestedDeploymentId = RequestedDeploymentId(UUID.randomUUID())

      implicit val deploymentIdCodec: PlainCodec[RequestedDeploymentId] =
        Codec.uuid.map(RequestedDeploymentId(_))(_.value)

    }

  }

}
