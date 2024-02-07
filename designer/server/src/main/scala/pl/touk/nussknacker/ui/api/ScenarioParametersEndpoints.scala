package pl.touk.nussknacker.ui.api

import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.ScenarioParametersEndpoints.Dtos.{EngineSetupDetails, ScenarioParametersCombination}
import sttp.model.StatusCode.Ok
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

class ScenarioParametersEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  lazy val scenarioParametersCombinationsEndpoint
      : SecuredEndpoint[Unit, Unit, List[ScenarioParametersCombination], Any] =
    baseNuApiEndpoint
      .summary("Service providing available combinations of scenario's parameters")
      .tag("App")
      .get
      .in("scenarioParametersCombinations")
      .out(
        statusCode(Ok).and(
          jsonBody[List[ScenarioParametersCombination]]
            .example(
              Example.of(
                summary = Some("List of available parameters combinations"),
                value = List(
                  ScenarioParametersCombination(
                    processingMode = ProcessingMode.UnboundedStream,
                    category = "Marketing",
                    engineSetup = EngineSetupDetails(EngineSetupName("Flink"), List.empty)
                  ),
                  ScenarioParametersCombination(
                    processingMode = ProcessingMode.RequestResponse,
                    category = "Fraud",
                    engineSetup = EngineSetupDetails(EngineSetupName("Lite K8s"), List.empty)
                  ),
                  ScenarioParametersCombination(
                    processingMode = ProcessingMode.UnboundedStream,
                    category = "Fraud",
                    engineSetup =
                      EngineSetupDetails(EngineSetupName("Flink Fraud Detection"), List("Invalid Flink configuration"))
                  )
                )
              )
            )
        )
      )
      .withSecurity(auth)

}

object ScenarioParametersEndpoints {

  object Dtos {

    @derive(encoder, decoder, schema)
    case class ScenarioParametersCombination(
        processingMode: ProcessingMode,
        category: String,
        // TODO Engine setup shouldn't be inside parameters. It should be rather picked on the stage when user decide where to deploy scenario
        engineSetup: EngineSetupDetails
    )

    @derive(encoder, decoder, schema)
    case class EngineSetupDetails(name: EngineSetupName, errors: List[String])

    object ScenarioParametersCombination {

      implicit val processNameSchema: Schema[ProcessingMode] = Schema.string

    }

    object EngineSetupDetails {

      implicit val engineSetupNameSchema: Schema[EngineSetupName] = Schema.string

    }

  }

}
