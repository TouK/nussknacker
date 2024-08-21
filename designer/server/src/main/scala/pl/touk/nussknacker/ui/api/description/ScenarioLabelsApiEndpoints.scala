package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.description.ScenarioLabelsApiEndpoints.Dtos.ScenarioLabels
import sttp.model.StatusCode.Ok
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

class ScenarioLabelsApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  lazy val scenarioLabelsEndpoint: SecuredEndpoint[Unit, Unit, ScenarioLabels, Any] =
    baseNuApiEndpoint
      .summary("Service providing available scenario labels")
      .tag("App")
      .get
      .in("scenarioLabels")
      .out(
        statusCode(Ok).and(
          jsonBody[ScenarioLabels]
            .example(
              Example.of(
                summary = Some("List of available scenario labels"),
                value = ScenarioLabels(
                  labels = List("Label 1", "Label 2")
                )
              )
            )
        )
      )
      .withSecurity(auth)

}

object ScenarioLabelsApiEndpoints {

  object Dtos {
    @derive(encoder, decoder, schema)
    final case class ScenarioLabels(labels: List[String])
  }

}
