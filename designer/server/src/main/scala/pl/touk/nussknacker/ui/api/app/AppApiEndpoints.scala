package pl.touk.nussknacker.ui.api.app

import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, Codec => CirceCodec}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.ui.api.BaseEndpointDefinitions
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.model.StatusCode.{Forbidden, InternalServerError, NoContent, Ok}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

private [app] object AppApiEndpoints extends BaseEndpointDefinitions {

  import AppApiEndpoints.Dtos.Codecs._
  import AppApiEndpoints.Dtos._

  def appHealthCheckEndpoint: Endpoint[Unit, Unit, Unit, HealthCheckProcessSuccessResponseDto.type, Any] =
    baseNuApiPublicEndpoint
      .get
      .in("app" / "healthCheck")
      .out(statusCode(Ok))
      .out(jsonBody[HealthCheckProcessSuccessResponseDto.type])

  def processDeploymentHealthCheckEndpoint(implicit user: LoggedUser): Endpoint[LoggedUser, Unit, HealthCheckProcessErrorResponseDto, HealthCheckProcessSuccessResponseDto.type, Any] =
    baseNuApiUserSecuredEndpoint(user)
      .get
      .in("app" / "healthCheck" / "process" / "deployment")
      .out(statusCode(Ok))
      .out(jsonBody[HealthCheckProcessSuccessResponseDto.type])
      .errorOut(statusCode(InternalServerError))
      .errorOut(jsonBody[HealthCheckProcessErrorResponseDto])

  def processValidationHealthCheckEndpoint(implicit user: LoggedUser): Endpoint[LoggedUser, Unit, HealthCheckProcessErrorResponseDto, HealthCheckProcessSuccessResponseDto.type, Any] =
    baseNuApiUserSecuredEndpoint(user)
      .get
      .in("app" / "healthCheck" / "process" / "validation")
      .out(statusCode(Ok))
      .out(jsonBody[HealthCheckProcessSuccessResponseDto.type])
      .errorOut(statusCode(InternalServerError))
      .errorOut(jsonBody[HealthCheckProcessErrorResponseDto])

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

  def userCategoriesWithProcessingTypesEndpoint(implicit user: LoggedUser): Endpoint[LoggedUser, Unit, Unit, UserCategoriesWithProcessingTypesDto, Any] =
    baseNuApiUserSecuredEndpoint(user)
      .get
      .in("app" / "config" / "categoriesWithProcessingType")
      .out(statusCode(Ok))
      .out(jsonBody[UserCategoriesWithProcessingTypesDto])

  def processingTypeDataReloadEndpoint(implicit user: LoggedUser): Endpoint[LoggedUser, Unit, ProcessingTypeDataReloadErrorDto, Unit, Any] =
    baseNuApiUserSecuredEndpoint(user)
      .post
      .in("app" / "processingtype" / "reload")
      .out(statusCode(NoContent))
      .errorOut(statusCode(Forbidden))
      .errorOut(plainBody[ProcessingTypeDataReloadErrorDto])

  object Dtos {

    object HealthCheckProcessSuccessResponseDto

    final case class HealthCheckProcessErrorResponseDto(message: Option[String],
                                                        processes: Option[Set[String]])

    final case class BuildInfoDto(name: String,
                                  gitCommit: String,
                                  buildTime: String,
                                  version: String,
                                  processingType: Map[String, Map[String, String]],
                                  otherProperties: Map[String, String])

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

    private[AppApiEndpoints] object Codecs {

      implicit val healthCheckProcessSuccessResponseDtoCodec: CirceCodec[HealthCheckProcessSuccessResponseDto.type] = {
        CirceCodec.from(
          Decoder.forProduct3[HealthCheckProcessSuccessResponseDto.type, String, Option[String], Option[Set[String]]](
            "status", "message", "processes"
          ) {
            case ("OK", None, None) =>
              HealthCheckProcessSuccessResponseDto
            case invalid =>
              throw new IllegalArgumentException(s"Cannot deserialize [$invalid]")
          },
          Encoder.forProduct3[HealthCheckProcessSuccessResponseDto.type, String, Option[String], Option[Set[String]]](
            "status", "message", "processes"
          )(
            _ => ("OK", None, None)
          )
        )
      }

      implicit val healthCheckProcessErrorResponseDtoCodec: CirceCodec[HealthCheckProcessErrorResponseDto] = {
        CirceCodec.from(
          Decoder.forProduct3[HealthCheckProcessErrorResponseDto, String, Option[String], Option[Set[String]]](
            "status", "message", "processes"
          ) {
            case ("ERROR", message, processes) =>
              HealthCheckProcessErrorResponseDto(message, processes)
            case invalid =>
              throw new IllegalArgumentException(s"Cannot deserialize [$invalid]")
          },
          Encoder.forProduct3[HealthCheckProcessErrorResponseDto, String, Option[String], Option[Set[String]]](
            "status", "message", "processes"
          )(
            dto => ("ERROR", dto.message, dto.processes)
          )
        )
      }

      implicit val buildInfoDtoCodec: CirceCodec[BuildInfoDto] = {
        CirceCodec.from(
          Decoder.instance { c =>
            for {
              name <- c.downField("name").as[String]
              version <- c.downField("version").as[String]
              buildTime <- c.downField("buildTime").as[String]
              gitCommit <- c.downField("gitCommit").as[String]
              processingType <- c.downField("processingType").as[Map[String, Map[String, String]]]
              otherProperties <- c.toMapExcluding("name", "version", "buildTime", "gitCommit", "processingType")
            } yield BuildInfoDto(name, gitCommit, buildTime, version, processingType, otherProperties)
          },
          Encoder.encodeJson.contramap { buildInfo =>
            buildInfo
              .otherProperties.asJson
              .deepMerge {
                Json
                  .obj(
                    "name" -> Json.fromString(buildInfo.name),
                    "version" -> Json.fromString(buildInfo.version),
                    "buildTime" -> Json.fromString(buildInfo.buildTime),
                    "gitCommit" -> Json.fromString(buildInfo.gitCommit),
                    "processingType" -> buildInfo.processingType.asJson
                  )
              }
          }
        )
      }

      implicit val serverConfigInfoDtoCodec: CirceCodec[ServerConfigInfoDto] = {
        CirceCodec.from(
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

      implicit val userCategoriesWithProcessingTypesDtoCodec: CirceCodec[UserCategoriesWithProcessingTypesDto] = {
        CirceCodec.from(
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
