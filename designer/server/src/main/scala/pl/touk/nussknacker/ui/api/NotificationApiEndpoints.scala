package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.notifications.Notification
import sttp.model.StatusCode.Ok
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.{EndpointInput, statusCode}

class NotificationApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  lazy val notificationEndpoint: SecuredEndpoint[Unit, Unit, List[Notification], Any] =
    baseNuApiEndpoint
      .summary("Endpoint to display notifications")
      .tag("Notifications")
      .get
      .in("notifications")
      .out(
        statusCode(Ok).and(
          jsonBody[List[Notification]]
        )
      )
      .withSecurity(auth)

}
