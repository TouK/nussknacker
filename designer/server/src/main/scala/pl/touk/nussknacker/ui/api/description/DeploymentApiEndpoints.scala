package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.description.DeploymentApiEndpoints.Dtos.DeploymentRequest
import sttp.model.StatusCode.Ok
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.{EndpointInput, path, statusCode}

class DeploymentApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  lazy val requestScenarioDeploymentEndpoint
      : SecuredEndpoint[(String, DeploymentRequest), Unit, DeploymentRequest, Any] =
    baseNuApiEndpoint
      .summary("Service allowing to request the deployment of a scenario")
      .put
      // FIXME: value class
      .in("deployment" / path[String]("deploymentId"))
      .in(jsonBody[DeploymentRequest])
      .out(
        statusCode(Ok).and(
          jsonBody[DeploymentRequest]
        )
      )
      .withSecurity(auth)

}

object DeploymentApiEndpoints {

  object Dtos {

    // FIXME: parameters
    @derive(encoder, decoder, schema)
    final case class DeploymentRequest()

  }

}
