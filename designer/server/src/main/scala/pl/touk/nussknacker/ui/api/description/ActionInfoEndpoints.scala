package pl.touk.nussknacker.ui.api.description

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, RawParameterEditor}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.ActionInfoHttpService.ActionInfoError
import pl.touk.nussknacker.ui.api.ActionInfoHttpService.ActionInfoError.NoScenario
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioNameCodec._
import pl.touk.nussknacker.ui.api.description.ActionInfoEndpoints.Dtos.{
  UiActionParameterConfigDto,
  UiActionParametersDto
}
import pl.touk.nussknacker.ui.api.description.ActionInfoEndpoints.Examples.noScenarioExample
import sttp.model.StatusCode.{NotFound, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

class ActionInfoEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  lazy val actionParametersEndpoint: SecuredEndpoint[ProcessName, ActionInfoError, UiActionParametersDto, Any] =
    baseNuApiEndpoint
      .summary("Get action parameters")
      .tag("Deployments")
      .get
      .in("actionInfo" / path[ProcessName]("scenarioName") / "parameters")
      .out(
        statusCode(Ok).and(
          jsonBody[UiActionParametersDto]
            .examples(
              List(
                Example.of(
                  summary = Some("Valid action parameters for given scenario"),
                  value = UiActionParametersDto(
                    Map(
                      ScenarioActionName.Deploy -> List(
                        // FIXME uncomment examples when discriminator (ParameterEditor) validation will work in the NuDesignerApiAvailableToExposeYamlSpec -
                        // UiActionNodeParameters(
                        //   NodeId("sample node id"),
                        //   Map("param name" -> UiActionParameterConfig.empty)
                        // )
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

    val emptyUiActionParameterConfig: UiActionParameterConfigDto =
      UiActionParameterConfigDto(None, RawParameterEditor, None, None)
  }

  object Dtos {

    @JsonCodec case class UiActionParametersDto(
        actionNameToParameters: Map[ScenarioActionName, List[UiActionNodeParametersDto]]
    )

    object UiActionParametersDto {

      implicit val uiActionParametersSchema: Schema[UiActionParametersDto] = {
        implicit val nodeIdSchema: Schema[NodeId]                   = Schema.string
        implicit val scenarioActionName: Schema[ScenarioActionName] = Schema.string
        implicit val parameterEditorSchema: Schema[ParameterEditor] =
          NodesApiEndpoints.Dtos.NodeValidationResultDto.parameterEditorSchema
        implicit val parameterSchema: Schema[UiActionParameterConfigDto]     = Schema.derived
        implicit val nodeParametersSchema: Schema[UiActionNodeParametersDto] = Schema.derived
        implicit val mapScenarioActionParametersSchema
            : Schema[Map[ScenarioActionName, List[UiActionNodeParametersDto]]] =
          Schema.schemaForMap[ScenarioActionName, List[UiActionNodeParametersDto]](_.value)
        Schema.derived
      }

    }

    @JsonCodec case class UiActionNodeParametersDto(
        nodeId: NodeId,
        componentId: String,
        parameters: Map[String, UiActionParameterConfigDto]
    )

    @JsonCodec final case class UiActionParameterConfigDto(
        defaultValue: Option[String],
        editor: ParameterEditor,
        label: Option[String],
        hintText: Option[String]
    )

  }

}
