package pl.touk.nussknacker.ui.api

import io.circe.generic.extras.{semiauto => extrassemiauto}
import io.circe.generic.semiauto
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.model.StatusCode.{Forbidden, NoContent, Ok}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

object AppResourcesEndpoints extends BaseEndpointDefinitions {

  import AppResourcesEndpoints.Dtos.Codecs._
  import AppResourcesEndpoints.Dtos._

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

  def serverConfigEndpoint(implicit user: LoggedUser): Endpoint[LoggedUser, Unit, ServerConfigInfoErrorDto, ServerConfigInfoDto, Any] =
    baseNuApiUserSecuredEndpoint(user)
      .get
      .in("app" / "config")
      .out(statusCode(Ok))
      .out(jsonBody[ServerConfigInfoDto])
      .errorOut(statusCode(Forbidden))
      .errorOut(plainBody[ServerConfigInfoErrorDto])

  def userCategoriesWithProcessingTypesEndpoint(implicit user: LoggedUser): Endpoint[LoggedUser, Unit, Unit, UserCategoriesWithProcessingTypesDto, Any] = {
    baseNuApiUserSecuredEndpoint(user)
      .get
      .in("app" / "config" / "categoriesWithProcessingType")
      .out(statusCode(Ok))
      .out(jsonBody[UserCategoriesWithProcessingTypesDto])
  }

  def processingTypeDataReloadEndpoint(implicit user: LoggedUser): Endpoint[LoggedUser, Unit, ProcessingTypeDataReloadErrorDto, Unit, Any] = {
    baseNuApiUserSecuredEndpoint(user)
      .post
      .in("app" / "processingtype" / "reload")
      .out(statusCode(NoContent))
      .errorOut(statusCode(Forbidden))
      .errorOut(plainBody[ProcessingTypeDataReloadErrorDto])
  }

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

    final case class BuildInfoDto(name: String,
                                  gitCommit: String,
                                  buildTime: String,
                                  version: String,
                                  processingType: Map[String, Map[String, String]])

    final case class ServerConfigInfoDto(configJson: Json)
    sealed trait ServerConfigInfoErrorDto
    object ServerConfigInfoErrorDto {
      case object AuthorizationServerConfigInfoErrorDto extends ServerConfigInfoErrorDto
    }

    final case class UserCategoriesWithProcessingTypesDto(map: Map[String, String])


    sealed trait ProcessingTypeDataReloadErrorDto
    object ProcessingTypeDataReloadErrorDto {
      case object AuthorizationProcessingTypeDataReloadErrorDto extends ProcessingTypeDataReloadErrorDto
    }

    private [AppResourcesEndpoints] object Codecs {
      implicit val healthCheckProcessResponseDtoCodec: io.circe.Codec[HealthCheckProcessResponseDto] = {
        implicit val statusCodec: io.circe.Codec[HealthCheckProcessResponseDto.Status] = extrassemiauto.deriveEnumerationCodec
        semiauto.deriveCodec
      }

      implicit val buildInfoDtoCodec: io.circe.Codec[BuildInfoDto] = {
        semiauto.deriveCodec
        // todo:
//        semiauto.deriveCodec
//        io.circe.Codec.from(
//          Decoder.decodeMap[String, String].map(m =>
//            BuildInfoDto(
//              m.view.filterKeys(_ == "processingType").toMap,
//              m.view.filterKeys(_ != "processingType")
//            )
//          ),
//          Encoder.encodeMap[String, String].contramap(_.info)
//        )
      }

      implicit val serverConfigInfoDtoCodec: io.circe.Codec[ServerConfigInfoDto] = {
        io.circe.Codec.from(
          Decoder.decodeJson.map(ServerConfigInfoDto.apply),
          Encoder.encodeJson.contramap[ServerConfigInfoDto](_.configJson)
        )
      }

      implicit val serverConfigInfoErrorDtoCodec: Codec[String, ServerConfigInfoErrorDto, CodecFormat.TextPlain] = {
        Codec
          .id(CodecFormat.TextPlain(), Schema.string[String])
          .map(
            Mapping
              .from[String, ServerConfigInfoErrorDto](
                _ => ServerConfigInfoErrorDto.AuthorizationServerConfigInfoErrorDto
              ) {
                case ServerConfigInfoErrorDto.AuthorizationServerConfigInfoErrorDto =>
                  "The supplied authentication is not authorized to access this resource"
              }
          )
      }

      implicit val userCategoriesWithProcessingTypesDtoCodec: io.circe.Codec[UserCategoriesWithProcessingTypesDto] = {
        io.circe.Codec.from(
          Decoder.decodeMap[String, String].map(UserCategoriesWithProcessingTypesDto.apply),
          Encoder.encodeMap[String, String].contramap(_.map)
        )
      }

      implicit val processingTypeDataReloadErrorDtoCodec: Codec[String, ProcessingTypeDataReloadErrorDto, CodecFormat.TextPlain] = {
        Codec
          .id(CodecFormat.TextPlain(), Schema.string[String])
          .map(
            Mapping
              .from[String, ProcessingTypeDataReloadErrorDto](
                _ => ProcessingTypeDataReloadErrorDto.AuthorizationProcessingTypeDataReloadErrorDto
              ) {
                case ProcessingTypeDataReloadErrorDto.AuthorizationProcessingTypeDataReloadErrorDto =>
                  "The supplied authentication is not authorized to access this resource"
              }
          )
      }
    }
  }
}
