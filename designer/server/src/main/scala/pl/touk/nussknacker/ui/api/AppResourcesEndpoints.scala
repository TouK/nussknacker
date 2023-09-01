package pl.touk.nussknacker.ui.api

import io.circe.generic.extras.{semiauto => extrassemiauto}
import io.circe.generic.semiauto
import pl.touk.nussknacker.ui.api.AppResourcesEndpoints.Dtos.{BuildInfoDto, HealthCheckProcessResponseDto}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.model.StatusCode.Ok
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

object AppResourcesEndpoints extends BaseEndpointDefinitions {

  import AppResourcesEndpoints.Dtos.Codecs._

  def healthCheckEndpoint: Endpoint[Unit, Unit, Unit, HealthCheckProcessResponseDto, Any] =
    baseNuApiPublicEndpoint
      .get
      .in("app" / "healthCheck")
      .out(statusCode(Ok))
      .out(jsonBody[HealthCheckProcessResponseDto])

  def buildInfoEndpoint: Endpoint[Unit, Unit, Unit, BuildInfoDto, Any] =
    baseNuApiPublicEndpoint
      .get
      .in("app" / "buildInfo")
      .out(statusCode(Ok))
      .out(jsonBody[BuildInfoDto])


  // todo: remove
  def healthCheckEndpoint2(implicit user: LoggedUser): Endpoint[LoggedUser, Unit, Unit, HealthCheckProcessResponseDto, Any] =
    baseNuApiSecuredEndpoint(user)
      .get
      .in("app" / "healthCheck")
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

    final case class BuildInfoDto(info: Map[String, String], processingType: Map[String, Map[String, String]])

    private [AppResourcesEndpoints] object Codecs {
      implicit val healthCheckProcessResponseDtoCodec: io.circe.Codec[HealthCheckProcessResponseDto] = {
        implicit val statusCodec: io.circe.Codec[HealthCheckProcessResponseDto.Status] = extrassemiauto.deriveEnumerationCodec
        semiauto.deriveCodec
      }

      implicit val buildInfoDtoCoded: io.circe.Codec[BuildInfoDto] = {
        semiauto.deriveCodec
//        io.circe.Codec.from(
//          Decoder.decodeMap[String, String].map(BuildInfoDto.apply),
//          Encoder.encodeMap[String, String].contramap(_.info)
//        )
      }
    }
  }
}
