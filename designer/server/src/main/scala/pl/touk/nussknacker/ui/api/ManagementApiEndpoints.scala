package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.validation.{CustomActionNonExistingError, CustomActionValidationError}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.{BaseEndpointDefinitions, CustomActionRequest}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import sttp.tapir.{EndpointInput, path, statusCode}
import TapirCodecs.ScenarioNameCodec._
import sttp.model.StatusCode.Ok
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import io.circe.generic.auto._
import pl.touk.nussknacker.engine.deployment.CustomActionValidationResult
import pl.touk.nussknacker.ui.NuDesignerError
import sttp.tapir._
import sttp.model.StatusCode
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

class ManagementApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  private lazy val baseProcessManagementEndpoint = baseNuApiEndpoint.in("processManagement")

  lazy val customActionValidationEndpoint: SecuredEndpoint[
    (ProcessName, CustomActionRequest),
    CustomActionValidationError,
    CustomActionValidationResult,
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
          jsonBody[CustomActionValidationResult]
        )
      )
      .errorOut(
        oneOf[CustomActionValidationError](
          oneOfVariantFromMatchType(
            StatusCode.BadRequest,
            jsonBody[CustomActionValidationError]
          )
        )
      )
      .withSecurity(auth)
  }

}
