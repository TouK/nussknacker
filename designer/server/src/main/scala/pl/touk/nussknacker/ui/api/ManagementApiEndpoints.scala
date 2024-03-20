package pl.touk.nussknacker.ui.api

import derevo.circe.encoder
import derevo.derive
import io.circe.generic.auto._
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.restmodel.{BaseEndpointDefinitions, CustomActionRequest}
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.ManagementApiEndpoints.ManagementApiError
import pl.touk.nussknacker.ui.api.ManagementApiEndpoints.ManagementApiError.{NoActionDefinition, NoScenario}
import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioNameCodec._
import pl.touk.nussknacker.ui.services.BaseHttpService.CustomAuthorizationError
import sttp.model.StatusCode
import sttp.model.StatusCode.Ok
import sttp.tapir.derevo.schema
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.{EndpointInput, path, statusCode, _}

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
        // GSK: Here I can specify what can go wrong in this particular endpoint
        // using group of ManagementApiError dedicated for ManagementApiEndpoints
        oneOf[ManagementApiError](
          oneOfVariantFromMatchType(
            StatusCode.NotFound,
            jsonBody[NoScenario]
          ),
          oneOfVariantFromMatchType(
            StatusCode.NotFound,
            jsonBody[NoActionDefinition]
          ),
        )
      )
      .withSecurity(auth)
  }

}

@derive(schema, encoder)
final case class CustomActionValidationDto(errors: List[NodeValidationError], validationPerformed: Boolean)

object ManagementApiEndpoints {

  sealed trait ManagementApiError

  // GSK: Here we have definitions of what might go wrong in "ManagementApiEndpoints".
  // Those classes/trait are in the scope of endpoint, not the scope of validation.
  // Validation has its own errors ("validation errors") and they are transformed into ManagementApiError ("endpoint errors").
  // Now we have customaction validation, but we want to get all endpoints from old ManagementResources (akka).
  // NoPermission is copied from NodesError.
  object ManagementApiError {
    final case object NoPermission                         extends ManagementApiError with CustomAuthorizationError
    final case class NoScenario(scenarioName: ProcessName) extends ManagementApiError
    final case class NoActionDefinition(scenarioName: ProcessName, actionName: ScenarioActionName)
        extends ManagementApiError

    // GSK: This is temporary, to handle either transfromations in CustomActionValidator. We want to define specific exceptions, this is too generic.
    final case object SomethingWentWrong extends ManagementApiError

    // GSK: copied from NodesError
    private def deserializationNotSupportedException =
      (ignored: Any) => throw new IllegalStateException("Deserializing errors is not supported.")

    implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, NoScenario](deserializationNotSupportedException)(e =>
          s"No scenario ${e.scenarioName} found"
        )
      )
    }

    implicit val noActionDefinitionCodec: Codec[String, NoActionDefinition, CodecFormat.TextPlain] = {
      Codec.string.map(
        Mapping.from[String, NoActionDefinition](deserializationNotSupportedException)(e =>
          s"No definition of action ${e.actionName} for scenario ${e.scenarioName} found"
        )
      )
    }

  }

}
