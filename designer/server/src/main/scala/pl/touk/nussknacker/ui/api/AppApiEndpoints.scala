package pl.touk.nussknacker.ui.api

import io.circe.syntax._
import io.circe.{Codec => CirceCodec, Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.ui.api.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.ui.security.api.AuthCredentials
import sttp.model.StatusCode._
import sttp.tapir.EndpointInput.Auth
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

class AppApiEndpoints(auth: Auth[AuthCredentials, _]) extends BaseEndpointDefinitions {

  import AppApiEndpoints.Dtos.Codecs._
  import AppApiEndpoints.Dtos._

  lazy val appHealthCheckEndpoint: PublicEndpoint[Unit, Unit, HealthCheckProcessSuccessResponseDto.type, Any] =
    baseNuApiEndpoint.get
      .in("app" / "healthCheck")
      .out(statusCode(Ok))
      .out(jsonBody[HealthCheckProcessSuccessResponseDto.type])

  lazy val processDeploymentHealthCheckEndpoint
      : SecuredEndpoint[Unit, HealthCheckProcessErrorResponseDto, HealthCheckProcessSuccessResponseDto.type, Any] =
    baseNuApiEndpoint.get
      .in("app" / "healthCheck" / "process" / "deployment")
      .out(
        statusCode(Ok).and(jsonBody[HealthCheckProcessSuccessResponseDto.type])
      )
      .errorOut(
        statusCode(InternalServerError).and(jsonBody[HealthCheckProcessErrorResponseDto])
      )
      .withSecurity(auth)

  lazy val processValidationHealthCheckEndpoint
      : SecuredEndpoint[Unit, HealthCheckProcessErrorResponseDto, HealthCheckProcessSuccessResponseDto.type, Any] =
    baseNuApiEndpoint.get
      .in("app" / "healthCheck" / "process" / "validation")
      .out(
        statusCode(Ok).and(jsonBody[HealthCheckProcessSuccessResponseDto.type])
      )
      .errorOut(
        statusCode(InternalServerError).and(jsonBody[HealthCheckProcessErrorResponseDto])
      )
      .withSecurity(auth)

  lazy val buildInfoEndpoint: PublicEndpoint[Unit, Unit, BuildInfoDto, Any] =
    baseNuApiEndpoint.get
      .in("app" / "buildInfo")
      .out(statusCode(Ok))
      .out(jsonBody[BuildInfoDto])

  lazy val serverConfigEndpoint: SecuredEndpoint[Unit, Unit, ServerConfigInfoDto, Any] =
    baseNuApiEndpoint
      .withSecurity(auth)
      .get
      .in("app" / "config")
      .out(
        statusCode(Ok).and(jsonBody[ServerConfigInfoDto])
      )

  lazy val userCategoriesWithProcessingTypesEndpoint
      : SecuredEndpoint[Unit, Unit, UserCategoriesWithProcessingTypesDto, Any] =
    baseNuApiEndpoint
      .withSecurity(auth)
      .get
      .in("app" / "config" / "categoriesWithProcessingType")
      .out(
        statusCode(Ok).and(jsonBody[UserCategoriesWithProcessingTypesDto])
      )

  lazy val processingTypeDataReloadEndpoint: SecuredEndpoint[Unit, Unit, Unit, Any] =
    baseNuApiEndpoint
      .withSecurity(auth)
      .post
      .in("app" / "processingtype" / "reload")
      .out(statusCode(NoContent))

}
object AppApiEndpoints {
  object Dtos {

    object HealthCheckProcessSuccessResponseDto

    final case class HealthCheckProcessErrorResponseDto(message: Option[String], processes: Option[Set[String]])

    final case class BuildInfoDto(
        name: String,
        gitCommit: String,
        buildTime: String,
        version: String,
        processingType: Map[String, Map[String, String]],
        otherProperties: Map[String, String]
    )

    final case class ServerConfigInfoDto(configJson: Json)

    final case class UserCategoriesWithProcessingTypesDto(map: Map[String, String])

    sealed trait ProcessingTypeDataReloadErrorDto

    private[AppApiEndpoints] object Codecs {

      implicit val healthCheckProcessSuccessResponseDtoCodec: CirceCodec[HealthCheckProcessSuccessResponseDto.type] = {
        CirceCodec.from(
          Decoder
            .forProduct3[HealthCheckProcessSuccessResponseDto.type, String, Option[String], Option[Set[String]]](
              "status",
              "message",
              "processes"
            ) {
              case ("OK", None, None) =>
                HealthCheckProcessSuccessResponseDto
              case invalid =>
                throw new IllegalArgumentException(s"Cannot deserialize [$invalid]")
            },
          Encoder
            .forProduct3[HealthCheckProcessSuccessResponseDto.type, String, Option[String], Option[Set[String]]](
              "status",
              "message",
              "processes"
            )(_ => ("OK", None, None))
        )
      }

      implicit val healthCheckProcessErrorResponseDtoCodec: CirceCodec[HealthCheckProcessErrorResponseDto] = {
        CirceCodec.from(
          Decoder
            .forProduct3[HealthCheckProcessErrorResponseDto, String, Option[String], Option[Set[String]]](
              "status",
              "message",
              "processes"
            ) {
              case ("ERROR", message, processes) =>
                HealthCheckProcessErrorResponseDto(message, processes)
              case invalid =>
                throw new IllegalArgumentException(s"Cannot deserialize [$invalid]")
            },
          Encoder
            .forProduct3[HealthCheckProcessErrorResponseDto, String, Option[String], Option[Set[String]]](
              "status",
              "message",
              "processes"
            )(dto => ("ERROR", dto.message, dto.processes))
        )
      }

      implicit val buildInfoDtoCodec: CirceCodec[BuildInfoDto] = {
        CirceCodec.from(
          Decoder.instance { c =>
            for {
              name            <- c.downField("name").as[String]
              version         <- c.downField("version").as[String]
              buildTime       <- c.downField("buildTime").as[String]
              gitCommit       <- c.downField("gitCommit").as[String]
              processingType  <- c.downField("processingType").as[Map[String, Map[String, String]]]
              otherProperties <- c.toMapExcluding("name", "version", "buildTime", "gitCommit", "processingType")
            } yield BuildInfoDto(name, gitCommit, buildTime, version, processingType, otherProperties)
          },
          Encoder.encodeJson.contramap { buildInfo =>
            buildInfo.otherProperties.asJson
              .deepMerge {
                Json
                  .obj(
                    "name"           -> Json.fromString(buildInfo.name),
                    "version"        -> Json.fromString(buildInfo.version),
                    "buildTime"      -> Json.fromString(buildInfo.buildTime),
                    "gitCommit"      -> Json.fromString(buildInfo.gitCommit),
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

      implicit val userCategoriesWithProcessingTypesDtoCodec: CirceCodec[UserCategoriesWithProcessingTypesDto] = {
        CirceCodec.from(
          Decoder.decodeMap[String, String].map(UserCategoriesWithProcessingTypesDto.apply),
          Encoder.encodeMap[String, String].contramap(_.map)
        )
      }
    }
  }
}
