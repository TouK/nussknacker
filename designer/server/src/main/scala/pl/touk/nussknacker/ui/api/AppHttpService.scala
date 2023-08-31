package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.api.AppResourcesEndpoints.Dtos.HealthCheckProcessResponseDto
import pl.touk.nussknacker.ui.api.AppResourcesEndpoints.Dtos.HealthCheckProcessResponseDto.Status
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.tapir.server.ServerEndpoint.Full

import scala.concurrent.Future

object AppHttpService {

  def serverEndpoint(implicit loggedUser: LoggedUser): Full[LoggedUser, LoggedUser, Unit, Unit, HealthCheckProcessResponseDto, Any, Future] = {
    AppResourcesEndpoints.healthCheckEndpoint
      .serverSecurityLogicSuccess[LoggedUser, Future](Future.successful)
      .serverLogic { loggedUser => _ =>
        Future.successful(Right(HealthCheckProcessResponseDto(Status.OK, Some(s"logged user: $loggedUser"), None)))
      }
  }
}
