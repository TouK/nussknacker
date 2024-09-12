package pl.touk.nussknacker.ui.api.description

import derevo.circe.encoder
import derevo.derive
import io.circe.generic.auto._
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.restmodel.{BaseEndpointDefinitions, CustomActionRequest}
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.description.ManagementApiEndpoints.Dtos.{CustomActionValidationDto, ManagementApiError, NoActionDefinition, NoScenario}
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioNameCodec._
import pl.touk.nussknacker.ui.api.TapirCodecs.ClassCodec._
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import sttp.model.StatusCode
import sttp.model.StatusCode.Ok
import sttp.tapir.derevo.schema
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir._

class ManagementApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  private lazy val baseProcessManagementEndpoint = baseNuApiEndpoint.in("processManagement")

  lazy val customActionValidationEndpoint: SecuredEndpoint[
    (ProcessName, CustomActionRequest),
    ManagementApiError,
    CustomActionValidationDto,
    Any
  ] = {
    baseProcessManagementEndpoint
      .summary("Endpoint to validate input in custom action fields")
      .tag("CustomAction")
      .post
      .in("customAction" / path[ProcessName]("scenarioName") / "validation")
      .in(jsonBody[CustomActionRequest])
      .out(
        statusCode(Ok).and(
          jsonBody[CustomActionValidationDto]
        )
      )
      .errorOut(
        oneOf[ManagementApiError](
          oneOfVariantFromMatchType(
            StatusCode.NotFound,
            plainBody[NoScenario]
          ),
          oneOfVariantFromMatchType(
            StatusCode.NotFound,
            plainBody[NoActionDefinition]
          ),
        )
      )
      .withSecurity(auth)
  }

}


object ManagementApiEndpoints {

  object Dtos {

    @derive(schema, encoder)
    final case class CustomActionValidationDto(validationErrors: List[NodeValidationError], validationPerformed: Boolean)

    sealed trait ManagementApiError
    final case object NoPermission                         extends ManagementApiError with CustomAuthorizationError
    final case class NoScenario(scenarioName: ProcessName) extends ManagementApiError
    final case class NoActionDefinition(scenarioName: ProcessName, actionName: ScenarioActionName)
        extends ManagementApiError

    implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario](e =>
        s"Couldn't find scenario ${e.scenarioName}"
      )
    }

    implicit val noActionDefinitionCodec: Codec[String, NoActionDefinition, CodecFormat.TextPlain] = {
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoActionDefinition](e =>
        s"Couldn't find definition of action ${e.actionName} for scenario ${e.scenarioName}"
      )
    }

  }

}
