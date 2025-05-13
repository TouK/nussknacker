package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.description.ScenarioParametersApiEndpoints.Dtos.ScenarioParametersCombinationWithEngineErrors
import sttp.model.StatusCode.Ok
import sttp.tapir._
import sttp.tapir.EndpointIO.Example
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

class ScenarioParametersApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  lazy val scenarioParametersCombinationsEndpoint
      : SecuredEndpoint[Unit, Unit, ScenarioParametersCombinationWithEngineErrors, Any] =
    baseNuApiEndpoint
      .summary("Service providing available combinations of scenario's parameters")
      .tag("App")
      .get
      .in("scenarioParametersCombinations")
      .out(
        statusCode(Ok).and(
          jsonBody[ScenarioParametersCombinationWithEngineErrors]
            .example(
              Example.of(
                summary = Some("List of available parameters combinations"),
                value = ScenarioParametersCombinationWithEngineErrors(
                  combinations = List(
                    ScenarioParameters(
                      processingMode = ProcessingMode.UnboundedStream,
                      category = "Marketing",
                      engineSetupName = EngineSetupName("Flink")
                    ),
                    ScenarioParameters(
                      processingMode = ProcessingMode.RequestResponse,
                      category = "Fraud",
                      engineSetupName = EngineSetupName("Lite K8s")
                    ),
                    ScenarioParameters(
                      processingMode = ProcessingMode.UnboundedStream,
                      category = "Fraud",
                      engineSetupName = EngineSetupName("Flink Fraud Detection")
                    )
                  ),
                  engineSetupErrors = Map(EngineSetupName("Flink") -> List("Invalid Flink configuration"))
                )
              )
            )
        )
      )
      .withSecurity(auth)

}

object ScenarioParametersApiEndpoints {

  object Dtos {

    @derive(encoder, decoder, schema)
    final case class ScenarioParametersCombinationWithEngineErrors(
        combinations: List[ScenarioParameters],
        engineSetupErrors: Map[EngineSetupName, List[String]]
    )

    object ScenarioParametersCombinationWithEngineErrors {

      implicit val processNameSchema: Schema[ProcessingMode] = Schema.string

      implicit val engineSetupNameSchema: Schema[EngineSetupName] = Schema.string

      implicit val scenarioParametersSchema: Schema[ScenarioParameters] = Schema.derived[ScenarioParameters]

      implicit val engineSetupErrorsSchema: Schema[Map[EngineSetupName, List[String]]] =
        Schema.schemaForMap[EngineSetupName, List[String]](_.value)

    }

  }

}
