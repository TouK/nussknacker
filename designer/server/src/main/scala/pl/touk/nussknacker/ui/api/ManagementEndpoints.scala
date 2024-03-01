package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.{BaseEndpointDefinitions, CustomActionRequest}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import sttp.tapir.{EndpointInput, path, statusCode}
import TapirCodecs.ScenarioNameCodec._
import sttp.model.StatusCode.Ok
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe._
import io.circe.generic.auto._
import sttp.tapir._

class ManagementEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  private lazy val baseProcessManagementEndpoint = baseNuApiEndpoint.in("processManagement")

  lazy val customActionValidationEndpoint: SecuredEndpoint[(ProcessName, CustomActionRequest), Unit, Unit, Any] = {
    baseProcessManagementEndpoint
      .summary("Endpoint to validate input in custom action fields")
      .tag("CustomAction")
      .post
      .in(path[ProcessName]("scenarioName") / "validation")
      .in(jsonBody[CustomActionRequest])
      .out(
        statusCode(Ok)
      )
      .errorOut()
      .withSecurity(auth)

  }

}
