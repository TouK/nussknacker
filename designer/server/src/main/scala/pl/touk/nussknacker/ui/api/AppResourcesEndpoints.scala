package pl.touk.nussknacker.ui.api

import io.circe.generic.semiauto
import io.circe.generic.extras.{semiauto => extrassemiauto}
import pl.touk.nussknacker.ui.api.AppResourcesEndpoints.Dtos.HealthCheckProcessResponseDto
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.model.StatusCode.Ok
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

object AppResourcesEndpoints extends BaseEndpointDefinitions {

  import AppResourcesEndpoints.Dtos.Codecs._

  def healthCheckEndpoint(implicit user: LoggedUser): Endpoint[LoggedUser, Unit, Unit, HealthCheckProcessResponseDto, Any] =
    baseNuApiSecuredEndpoint(user)
      .get
      .in("app" / "healthCheck") // todo: remove
      .out(statusCode(Ok))
      .out(jsonBody[HealthCheckProcessResponseDto])

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

    private [AppResourcesEndpoints] object Codecs {
      implicit val healthCheckProcessResponseDtoCodec: io.circe.Codec[HealthCheckProcessResponseDto] = {
        implicit val statusCodec: io.circe.Codec[HealthCheckProcessResponseDto.Status] = extrassemiauto.deriveEnumerationCodec
        semiauto.deriveCodec
      }
    }
  }
}
