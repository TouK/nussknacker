package pl.touk.nussknacker.restmodel.component

import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import sttp.model.StatusCode.{NotFound, Ok}
import sttp.tapir._
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody
import pl.touk.nussknacker.ui.security.api.AuthCredentials



class ComponentResourceApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  val componentsListEndpoint: SecuredEndpoint[Unit, Unit, List[ComponentListElement], Any] =
    baseNuApiEndpoint
      .summary("Listing components")
      .tag("Components")
      .withSecurity(auth)
      .get
      .in("components")
      .out(
        statusCode(Ok).and(
          jsonBody[List[ComponentListElement]]
        )
      )

  val componentUsageEndpoint: SecuredEndpoint[String, String, List[ComponentUsagesInScenario], Any] =
    baseNuApiEndpoint
      .summary("Show component usage")
      .tag("Components")
      .get
      .in("components" / path[String]("id") / "usages")
      .out(
        statusCode(Ok).and(
          jsonBody[List[ComponentUsagesInScenario]]
        )
      )
      .errorOut(
        statusCode(NotFound).and(
          stringBody
            .example("Component this not exist.")
        )
      )
      .withSecurity(auth)

}
