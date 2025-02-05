package pl.touk.nussknacker.ui.api.description

import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.ActionInfoHttpService.ActionInfoError
import pl.touk.nussknacker.ui.api.ActionInfoHttpService.ActionInfoError.NoScenario
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioNameCodec._
import pl.touk.nussknacker.ui.api.description.ActionInfoEndpoints.Examples.noScenarioExample
import pl.touk.nussknacker.ui.api.description.ActionInfoEndpoints._
import pl.touk.nussknacker.ui.process.deployment.ActionInfoService.{
  UiActionNodeParameters,
  UiActionParameterConfig,
  UiActionParameters
}
import sttp.model.StatusCode.{NotFound, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

class ActionInfoEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  lazy val actionParametersEndpoint: SecuredEndpoint[ProcessName, ActionInfoError, UiActionParameters, Any] =
    baseNuApiEndpoint
      .summary("Get action parameters")
      .tag("Deployments")
      .get
      .in("actionInfo" / path[ProcessName]("scenarioName") / "parameters")
      .out(
        statusCode(Ok).and(
          jsonBody[UiActionParameters]
            .examples(
              List(
                Example.of(
                  summary = Some("Valid action parameters for given scenario"),
                  value = UiActionParameters(
                    Map(
                      ScenarioActionName.Deploy -> List(
                        UiActionNodeParameters(
                          NodeId("sample node id"),
                          Map("param name" -> UiActionParameterConfig.empty)
                        )
                      )
                    )
                  )
                )
              )
            )
        )
      )
      .errorOut(
        oneOf[ActionInfoError](
          noScenarioExample
        )
      )
      .withSecurity(auth)

}

object ActionInfoEndpoints {

  implicit val uiActionParametersSchema: Schema[UiActionParameters] = Schema.anyObject

  object Examples {

    val noScenarioExample: EndpointOutput.OneOfVariant[NoScenario] =
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

  }

}
