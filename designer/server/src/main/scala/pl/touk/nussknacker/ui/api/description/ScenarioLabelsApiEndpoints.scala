package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.description.ScenarioLabelsApiEndpoints.Dtos.{
  ScenarioLabels,
  ScenarioLabelsValidationRequestDto,
  ScenarioLabelsValidationResponseDto,
  ValidationError
}
import sttp.model.StatusCode.Ok
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

class ScenarioLabelsApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  lazy val scenarioLabelsEndpoint: SecuredEndpoint[Unit, Unit, ScenarioLabels, Any] =
    baseNuApiEndpoint
      .summary("Service providing available scenario labels")
      .tag("Scenario labels")
      .get
      .in("scenarioLabels")
      .out(
        statusCode(Ok).and(
          jsonBody[ScenarioLabels]
            .example(
              Example.of(
                summary = Some("List of available scenario labels"),
                value = ScenarioLabels(
                  labels = List("Label_1", "Label_2")
                )
              )
            )
        )
      )
      .withSecurity(auth)

  lazy val validateScenarioLabelsEndpoint
      : SecuredEndpoint[ScenarioLabelsValidationRequestDto, Unit, ScenarioLabelsValidationResponseDto, Any] =
    baseNuApiEndpoint
      .summary("Service providing scenario labels validation")
      .tag("Scenario labels")
      .post
      .in("scenarioLabels" / "validation")
      .in(
        jsonBody[ScenarioLabelsValidationRequestDto]
          .examples(
            List(
              Example.of(
                summary = Some("List of valid scenario labels"),
                value = ScenarioLabelsValidationRequestDto(
                  labels = List("Label_1", "Label_2")
                )
              ),
              Example.of(
                summary = Some("List of scenario labels with invalid one"),
                value = ScenarioLabelsValidationRequestDto(
                  labels = List("Label_1", "Label_2", "Label 3")
                )
              )
            )
          )
      )
      .out(
        statusCode(Ok).and(
          jsonBody[ScenarioLabelsValidationResponseDto]
            .examples(
              List(
                Example.of(
                  summary = Some("Validation response with no errors"),
                  value = ScenarioLabelsValidationResponseDto(
                    validationErrors = List.empty
                  )
                ),
                Example.of(
                  summary = Some("Validation response with errors"),
                  value = ScenarioLabelsValidationResponseDto(
                    validationErrors = List(
                      ValidationError(
                        label = "Label 3",
                        messages = List("Scenario label can contain only alphanumeric characters, '-' and '_'")
                      )
                    )
                  )
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

    @derive(encoder, decoder, schema)
    final case class ScenarioLabelsValidationRequestDto(labels: List[String])

    @derive(encoder, decoder, schema)
    final case class ScenarioLabelsValidationResponseDto(validationErrors: List[ValidationError])

    @derive(encoder, decoder, schema)
    final case class ValidationError(label: String, messages: List[String])
  }

}
