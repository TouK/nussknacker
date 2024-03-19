package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.api.CirceUtil._
import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.ui.validation.CustomActionValidationError
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.{BaseEndpointDefinitions, CustomActionRequest}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import sttp.tapir.{EndpointInput, derevo, path, statusCode, _}
import TapirCodecs.ScenarioNameCodec._
import sttp.model.StatusCode.Ok
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import io.circe.generic.auto._
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.deployment.CustomActionValidationResult
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import sttp.model.StatusCode
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.derevo.schema

class ManagementApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  private lazy val baseProcessManagementEndpoint = baseNuApiEndpoint.in("processManagement")

  lazy val customActionValidationEndpoint: SecuredEndpoint[
    (ProcessName, CustomActionRequest),
    CustomActionValidationError,
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

@derive(schema, encoder)
final case class CustomActionValidationDto(errors: List[NodeValidationError], validationPerformed: Boolean)
