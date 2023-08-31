package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.api.AppResourcesEndpoints.Dtos.HealthCheckProcessResponseDto
import sttp.tapir._
import sttp.tapir.json.circe._

class AppResourcesEndpoints extends BaseEndpointDefinitions {

  val healthCheckEndpoint =
    baseNuApiEndpoint
      .get
      .in("app" / "healthCheck")
      .out(jsonBody[HealthCheckProcessResponseDto])

}

object AppResourcesEndpoints {

  object Dtos {

    final case class HealthCheckProcessResponseDto(status: HealthCheckProcessResponseDto.Status,
                                                   message: Option[String],
                                                   processes: Option[Set[String]])
    object HealthCheckProcessResponseDto {

      sealed trait Status
      object Status {
        case object OK extends Status
        case object ERROR extends Status
      }
    }


  }
}
