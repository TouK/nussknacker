package pl.touk.nussknacker.ui.api.app

import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.ui.api.BaseEndpointDefinitions
import pl.touk.nussknacker.ui.api.BaseEndpointDefinitions._
import pl.touk.nussknacker.ui.security.api.AuthCredentials
import sttp.model.StatusCode._
import sttp.tapir.EndpointInput.Auth
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

private[api] class AppApiEndpoints(auth: Auth[AuthCredentials, _])
  extends BaseEndpointDefinitions(auth) {

  import AppApiEndpoints.Dtos.Codecs._
  import AppApiEndpoints.Dtos._

  lazy val appHealthCheckEndpoint: PublicEndpoint[Unit, Unit, HealthCheckProcessSuccessResponseDto.type, Any] =
    baseNuApiPublicEndpoint
      .get
      .in("app" / "healthCheck")
      .out(statusCode(Ok))
      .out(jsonBody[HealthCheckProcessSuccessResponseDto.type])

  lazy val processDeploymentHealthCheckEndpoint =
    baseNuApiPublicEndpoint
      .get
      .in("app" / "healthCheck" / "process" / "deployment")
      .out(
        statusCode(Ok).and(jsonBody[HealthCheckProcessSuccessResponseDto.type])
      )
      .errorOut(
        statusCode(InternalServerError).and(jsonBody[HealthCheckProcessErrorResponseDto])
      )
      .withSecurity(auth)

  lazy val processValidationHealthCheckEndpoint =
    baseNuApiPublicEndpoint
      .get
      .in("app" / "healthCheck" / "process" / "validation")
      .out(
        statusCode(Ok).and(jsonBody[HealthCheckProcessSuccessResponseDto.type])
      )
      .errorOut(
        statusCode(InternalServerError).and(jsonBody[HealthCheckProcessErrorResponseDto])
      )
      .withSecurity(auth)

  lazy val buildInfoEndpoint: Endpoint[Unit, Unit, Unit, BuildInfoDto, Any] =
    baseNuApiPublicEndpoint
      .get
      .in("app" / "buildInfo")
      .out(statusCode(Ok))
      .out(jsonBody[BuildInfoDto])

  // todo: conditionally excluded from API docs as well
  lazy val serverConfigEndpoint =
    baseNuApiPublicEndpoint
      .withSecurity(auth)
      .get
      .in("app" / "config")
      .out(
        statusCode(Ok).and(jsonBody[ServerConfigInfoDto])
      )

  lazy val userCategoriesWithProcessingTypesEndpoint =
    baseNuApiPublicEndpoint
      .withSecurity(auth)
      .get
      .in("app" / "config" / "categoriesWithProcessingType")
      .out(
        statusCode(Ok).and(jsonBody[UserCategoriesWithProcessingTypesDto])
      )

  lazy val processingTypeDataReloadEndpoint =
    baseNuApiPublicEndpoint
      .withSecurity(auth)
      .post
      .in("app" / "processingtype" / "reload")
      .out(statusCode(NoContent))

  override val allEndpoints: List[AnyEndpoint] = List(
    buildInfoEndpoint,
    serverConfigEndpoint,
    userCategoriesWithProcessingTypesEndpoint,
    appHealthCheckEndpoint,
    processDeploymentHealthCheckEndpoint,
    processValidationHealthCheckEndpoint,
    processingTypeDataReloadEndpoint
  )

}
object AppApiEndpoints {
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

    final case class UserCategoriesWithProcessingTypesDto(map: Map[String, String])

    sealed trait ProcessingTypeDataReloadErrorDto

    private[AppApiEndpoints] object Codecs {

      implicit val healthCheckProcessSuccessResponseDtoCodec: io.circe.Codec[HealthCheckProcessSuccessResponseDto.type] = {
        io.circe.Codec.from(
          Decoder
            .forProduct3[HealthCheckProcessSuccessResponseDto.type, String, Option[String], Option[Set[String]]](
            "status", "message", "processes"
          ) {
            case ("OK", None, None) =>
              HealthCheckProcessSuccessResponseDto
            case invalid =>
              throw new IllegalArgumentException(s"Cannot deserialize [$invalid]")
          },
          Encoder
            .forProduct3[HealthCheckProcessSuccessResponseDto.type, String, Option[String], Option[Set[String]]](
            "status", "message", "processes"
          )(
            _ => ("OK", None, None)
          )
        )
      }

      implicit val healthCheckProcessErrorResponseDtoCodec: io.circe.Codec[HealthCheckProcessErrorResponseDto] = {
        io.circe.Codec.from(
          Decoder
            .forProduct3[HealthCheckProcessErrorResponseDto, String, Option[String], Option[Set[String]]](
              "status", "message", "processes"
            ) {
              case ("ERROR", message, processes) =>
                HealthCheckProcessErrorResponseDto(message, processes)
              case invalid =>
                throw new IllegalArgumentException(s"Cannot deserialize [$invalid]")
            },
          Encoder
            .forProduct3[HealthCheckProcessErrorResponseDto, String, Option[String], Option[Set[String]]](
              "status", "message", "processes"
            )(
              dto => ("ERROR", dto.message, dto.processes)
            )
        )
      }

      implicit val buildInfoDtoCodec: io.circe.Codec[BuildInfoDto] = {
        io.circe.Codec.from(
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

      implicit val serverConfigInfoDtoCodec: io.circe.Codec[ServerConfigInfoDto] = {
        io.circe.Codec.from(
          Decoder.decodeJson.map(ServerConfigInfoDto.apply),
          Encoder.encodeJson.contramap[ServerConfigInfoDto](_.configJson)
        )
      }

      // todo: io.circe.Codec to CirceCodec
      implicit val userCategoriesWithProcessingTypesDtoCodec: io.circe.Codec[UserCategoriesWithProcessingTypesDto] = {
        io.circe.Codec.from(
          Decoder.decodeMap[String, String].map(UserCategoriesWithProcessingTypesDto.apply),
          Encoder.encodeMap[String, String].contramap(_.map)
        )
      }
    }
  }
}