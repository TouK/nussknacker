package pl.touk.nussknacker.ui.api.description

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.notifications.{DataToRefresh, Notification}
import sttp.model.StatusCode.Ok
import sttp.tapir.EndpointIO.Example
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
          jsonBody[List[Notification]].example(
            Example.of(
              summary = Some("Display simple deployment notification"),
              value = List(
                Notification(
                  id = "0351c45a-2c4c-4ffd-8848-ae6c2f281ef1",
                  scenarioName = Some(ProcessName("scenario1")),
                  message = "Deployment finished",
                  `type` = None,
                  toRefresh = List(
                    DataToRefresh(DataToRefresh.versions.id),
                    DataToRefresh(DataToRefresh.activity.id),
                    DataToRefresh(DataToRefresh.state.id)
                  )
                )
              )
            )
          )
        )
      )
      .withSecurity(auth)

}
