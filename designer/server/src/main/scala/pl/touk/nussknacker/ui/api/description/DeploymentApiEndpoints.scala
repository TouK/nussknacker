package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.TapirCodecs
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.DeploymentError.NoScenario
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.{
  DeploymentError,
  DeploymentRequest,
  RequestedDeploymentId
}
import sttp.model.StatusCode.{NotFound, Ok}
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
      .in(jsonBody[DeploymentRequest])
      .out(statusCode(Ok).and(jsonBody[Unit]))
      .errorOut(
        oneOf[DeploymentError](
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
      )
      .withSecurity(auth)

}

object DeploymentApiEndpoints {

  object Dtos {

    // TODO:
    //  - parameters passed to scenario
    //  - scenario graph version
    @derive(encoder, decoder, schema)
    final case class DeploymentRequest()

    sealed trait DeploymentError

    object DeploymentError {

      case class NoScenario(scenarioName: ProcessName) extends DeploymentError

      private def deserializationNotSupportedException =
        (ignored: Any) => throw new IllegalStateException("Deserializing errors is not supported.")

      implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] = {
        Codec.string.map(
          Mapping.from[String, NoScenario](deserializationNotSupportedException)(e =>
            s"No scenario ${e.scenarioName} found"
          )
        )
      }

    }

    final case class RequestedDeploymentId(value: String)

    object RequestedDeploymentId {

      private def encode(deploymentId: RequestedDeploymentId): String = deploymentId.value

      private def decode(value: String): DecodeResult[RequestedDeploymentId] = {
        DecodeResult.Value(RequestedDeploymentId(value))
      }

      implicit val deploymentIdCodec: PlainCodec[RequestedDeploymentId] = Codec.string.mapDecode(decode)(encode)

    }

  }

}
