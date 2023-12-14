package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.notifications.Notification
import sttp.model.StatusCode.{BadRequest, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.{EndpointInput, query, statusCode, stringBody}

class NotificationApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  lazy val notificationEndpoint: SecuredEndpoint[Option[String], String, List[Notification], Any] =
    baseNuApiEndpoint
      .summary("Endpoint to display notifications")
      .tag("Notifications")
      .get
      .in("notifications")
      .in(
        query[Option[String]]("after")
          .example(
            Example.of(
              summary = Some("Example usage - to work properly need to add \" on both sides of parameter"),
              value = Some(String.valueOf("\"2023-07-29T19:30:00Z\""))
            )
          )
      )
      .errorOut(
        statusCode(BadRequest).and(
          stringBody
            .example(
              Example.of(
                summary = Some("Number passed instead of Date"),
                value = s"The query parameter 'after' was malformed:\n" +
                  "DecodingFailure at : Got value '2000' with wrong type, expecting string"
              )
            )
        )
      )
      .out(
        statusCode(Ok).and(
          jsonBody[List[Notification]]
        )
      )
      .withSecurity(auth)

}
