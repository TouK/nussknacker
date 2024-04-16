package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveUnwrappedDecoder, deriveUnwrappedEncoder}
import io.circe.generic.semiauto.deriveEncoder
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.{NodeDeploymentData, NodesDeploymentData, SqlFilteringExpression}
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
import pl.touk.nussknacker.ui.listener.Comment
import sttp.model.StatusCode
import sttp.model.StatusCode.Ok
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

class DeploymentApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import TapirCodecs.ScenarioNameCodec._

  lazy val requestScenarioDeploymentEndpoint
      : SecuredEndpoint[(ProcessName, RequestedDeploymentId, DeploymentRequest), DeploymentError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Service allowing to request the deployment of a scenario")
      .put
      .in("scenarios" / path[ProcessName]("scenarioName") / "deployments" / path[RequestedDeploymentId]("deploymentId"))
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
      .out(statusCode(Ok))
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

}

object DeploymentApiEndpoints {

  object Dtos {

    // TODO: scenario graph version / the currently active version instead of the latest
    @derive(encoder, decoder, schema)
    final case class DeploymentRequest(
        nodesDeploymentData: NodesDeploymentData,
        comment: Option[ApiComment]
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

    final case class RequestedDeploymentId(value: String)

    object RequestedDeploymentId {

      implicit val deploymentIdCodec: PlainCodec[RequestedDeploymentId] =
        Codec.string.map(RequestedDeploymentId(_))(_.value)

    }

    final case class ApiComment(override val value: String) extends Comment

    object ApiComment {

      implicit val encoder: Encoder[ApiComment] = deriveUnwrappedEncoder

      implicit val decoder: Decoder[ApiComment] = deriveUnwrappedDecoder

      implicit val schema: Schema[ApiComment] = Schema.string

    }

  }

}
