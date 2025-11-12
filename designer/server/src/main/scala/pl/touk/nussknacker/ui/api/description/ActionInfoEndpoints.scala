package pl.touk.nussknacker.ui.api.description

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.definition.{
  FixedExpressionValue,
  FixedExpressionValueWithIcon,
  MultiSelectFixedValue,
  StaticParameterEditor,
  StaticStringParameterEditor
}
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.ActionInfoHttpService.ActionInfoError
import pl.touk.nussknacker.ui.api.ActionInfoHttpService.ActionInfoError.{CannotCompileScenario, NoScenario}
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioNameCodec._
import pl.touk.nussknacker.ui.api.description.ActionInfoEndpoints.Dtos.{
  UiActionParameterConfigDto,
  UiActionParametersDto
}
import pl.touk.nussknacker.ui.api.description.ActionInfoEndpoints.Examples.{
  cannotCompileScenarioExample,
  noScenarioExample
}
import sttp.model.StatusCode.{BadRequest, NotFound, Ok}
import sttp.tapir._
import sttp.tapir.EndpointIO.Example
import sttp.tapir.json.circe.jsonBody

import java.time.Duration
import java.time.temporal.ChronoUnit

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
          noScenarioExample,
          cannotCompileScenarioExample,
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

    val cannotCompileScenarioExample: EndpointOutput.OneOfVariant[CannotCompileScenario.type] =
      oneOfVariantFromMatchType(
        BadRequest,
        plainBody[CannotCompileScenario.type]
          .example(
            Example.of(
              summary = Some("Cannot compile scenario"),
              value = CannotCompileScenario
            )
          )
      )

    val emptyUiActionParameterConfig: UiActionParameterConfigDto =
      UiActionParameterConfigDto(None, StaticStringParameterEditor, None, None)
  }

  object Dtos {

    @JsonCodec case class UiActionParametersDto(
        actionNameToParameters: Map[ScenarioActionName, List[UiActionNodeParametersDto]]
    )

    object UiActionParametersDto {

      implicit val uiActionParametersSchema: Schema[UiActionParametersDto] = {
        implicit val nodeIdSchema: Schema[NodeId]                   = Schema.string
        implicit val scenarioActionName: Schema[ScenarioActionName] = Schema.string
        implicit lazy val durationSchema: Schema[Duration]          = Schema.schemaForJavaDuration
        implicit val chronoUnitSchema: Schema[ChronoUnit] = NodesApiEndpoints.Dtos.NodeValidationResultDto.timeSchema
        implicit val fixedExpressionSchema: Schema[FixedExpressionValue] =
          NodesApiEndpoints.Dtos.fixedExpressionValueSchema
        implicit val fixedExpressionWithIconSchema: Schema[FixedExpressionValueWithIcon] =
          NodesApiEndpoints.Dtos.fixedExpressionValueWithIconSchema
        implicit val selectOptionSchema: Schema[MultiSelectFixedValue] = NodesApiEndpoints.Dtos.selectOptionValueSchema
        implicit val parameterEditorSchema: Schema[StaticParameterEditor]    = Schema.derived
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
        editor: StaticParameterEditor,
        label: Option[String],
        hintText: Option[String]
    )

  }

}
