package pl.touk.nussknacker.ui.api

import derevo.circe.{decoder, encoder}
import derevo.derive
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, Json, Codec => CirceCodec}
import pl.touk.nussknacker.engine.api.CirceUtil.HCursorExt
import pl.touk.nussknacker.ui.api.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.ui.security.api.AuthCredentials
import sttp.model.StatusCode.{InternalServerError, NoContent, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir.EndpointInput.Auth
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

class AppApiEndpoints(auth: Auth[AuthCredentials, _])
  extends BaseEndpointDefinitions {

  import AppApiEndpoints.Dtos._

  lazy val appHealthCheckEndpoint: PublicEndpoint[Unit, Unit, HealthCheckProcessSuccessResponseDto, Any] =
    baseNuApiEndpoint
      .summary("Application health check service")
      .tag("App")
      .get
      .in("app" / "healthCheck")
      .out(
        statusCode(Ok).and(
          jsonBody[HealthCheckProcessSuccessResponseDto]
            .example(
              Example.of(
                summary = Some("Application is healthy"),
                value = HealthCheckProcessSuccessResponseDto()
              )
            )
        )
      )

  lazy val processDeploymentHealthCheckEndpoint: SecuredEndpoint[Unit, HealthCheckProcessErrorResponseDto, HealthCheckProcessSuccessResponseDto, Any] =
    baseNuApiEndpoint
      .summary("Deployed processes health check service")
      .tag("App")
      .get
      .in("app" / "healthCheck" / "process" / "deployment")
      .out(
        statusCode(Ok)
          .and(
            jsonBody[HealthCheckProcessSuccessResponseDto]
              .example(
                Example.of(
                  summary = Some("All deployed processes are healthy"),
                  value = HealthCheckProcessSuccessResponseDto()
                )
              )
          )
      )
      .errorOut(
        statusCode(InternalServerError).and(
          jsonBody[HealthCheckProcessErrorResponseDto]
            .example(
              Example.of(
                summary = Some("Some processes are unhealthy"),
                value = HealthCheckProcessErrorResponseDto(
                  message = Some("Scenarios with status PROBLEM"),
                  processes = Some(Set("process1", "process2"))
                )
              )
            )
        )
      )
      .withSecurity(auth)

  lazy val processValidationHealthCheckEndpoint: SecuredEndpoint[Unit, HealthCheckProcessErrorResponseDto, HealthCheckProcessSuccessResponseDto, Any] =
    baseNuApiEndpoint
      .summary("Deployed processes validation service")
      .tag("App")
      .get
      .in("app" / "healthCheck" / "process" / "validation")
      .out(
        statusCode(Ok).and(
          jsonBody[HealthCheckProcessSuccessResponseDto]
            .example(
              Example.of(
                summary = Some("There are no validation errors among the processes"),
                value = HealthCheckProcessSuccessResponseDto()
              )
            )
        )
      )
      .errorOut(
        statusCode(InternalServerError).and(
          jsonBody[HealthCheckProcessErrorResponseDto]
            .example(
              Example.of(
                summary = Some("Some processes have validation errors"),
                value = HealthCheckProcessErrorResponseDto(
                  message = Some("Scenarios with validation errors"),
                  processes = Some(Set("process2", "process3"))
                )
              )
            )
        )
      )
      .withSecurity(auth)

  lazy val buildInfoEndpoint: PublicEndpoint[Unit, Unit, BuildInfoDto, Any] =
    baseNuApiEndpoint
      .summary("Application info service")
      .tag("App")
      .get
      .in("app" / "buildInfo")
      .out(
        statusCode(Ok).and(
          jsonBody[BuildInfoDto]
            .example(
              Example.of(
                summary = Some("Application build info response"),
                value = BuildInfoDto(
                  name = "nussknacker",
                  gitCommit = "d4e42ee5c594ffe70a37faca3579eb535dac9820",
                  buildTime = "2023-09-25T09:26:30.402299",
                  version = "1.12.0-SNAPSHOT",
                  processingType = Map(
                    "streaming" -> Map(
                      "process-version" -> "0.1",
                      "engine-version" -> "0.2",
                      "generation-time" -> "2023-09-25T09:26:30.402299"
                    )
                  ),
                  otherProperties = Map.empty
                )
              )
            )
        )
      )

  lazy val serverConfigEndpoint: SecuredEndpoint[Unit, Unit, ServerConfigInfoDto, Any] =
    baseNuApiEndpoint
      .summary("Server configuration viewer service")
      .tag("App")
      .withSecurity(auth)
      .get
      .in("app" / "config")
      .out(
        statusCode(Ok).and(
          jsonBody[ServerConfigInfoDto]
            .example(
              Example.of(
                summary = Some("Application build info response"),
                value = ServerConfigInfoDto()
              )
            )
        )
      )

  lazy val userCategoriesWithProcessingTypesEndpoint: SecuredEndpoint[Unit, Unit, UserCategoriesWithProcessingTypesDto, Any] =
    baseNuApiEndpoint
      .summary("Configured categories with their processing types service")
      .tag("App")
      .withSecurity(auth)
      .get
      .in("app" / "config" / "categoriesWithProcessingType")
      .out(
        statusCode(Ok).and(
          jsonBody[UserCategoriesWithProcessingTypesDto]
            .example(
              Example.of(
                summary = Some("Configured categories and their processing types"),
                value = UserCategoriesWithProcessingTypesDto(
                  Map(
                    "Category1" -> "streaming",
                    "Category2" -> "streaming"
                  )
                )
              )
            )
        )
      )

  lazy val processingTypeDataReloadEndpoint: SecuredEndpoint[Unit, Unit, Unit, Any] =
    baseNuApiEndpoint
      .summary("Processing type data reload service")
      .tag("App")
      .withSecurity(auth)
      .post
      .in("app" / "processingtype" / "reload")
      .out(
        statusCode(NoContent).and(
          emptyOutput
            .example(
              Example.of(
                summary = Some("Reload done"),
                value = ()
              )
            )
        )
      )

}
object AppApiEndpoints {
  object Dtos {

    @derive(encoder, decoder, schema)
    final case class HealthCheckProcessSuccessResponseDto private(status: String,
                                                                  message: Option[String],
                                                                  processes: Option[Set[String]])
    object HealthCheckProcessSuccessResponseDto {
      def apply() = new HealthCheckProcessSuccessResponseDto(status = "OK", message = None, processes = None)
    }

    @derive(encoder, decoder, schema)
    final case class HealthCheckProcessErrorResponseDto private(status: String,
                                                                message: Option[String],
                                                                processes: Option[Set[String]])
    object HealthCheckProcessErrorResponseDto {
      def apply(message: Option[String],
                processes: Option[Set[String]]) =
        new HealthCheckProcessErrorResponseDto(status = "ERROR", message, processes)
    }

    final case class BuildInfoDto(name: String,
                                  gitCommit: String,
                                  buildTime: String,
                                  version: String,
                                  processingType: Map[String, Map[String, String]],
                                  otherProperties: Map[String, String])
    object BuildInfoDto {

      implicit val schema: Schema[BuildInfoDto] = Schema.string[BuildInfoDto]  // todo:

      implicit val circeCodec: CirceCodec[BuildInfoDto] = {
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
    }

    @derive(encoder, decoder, schema)
    final case class ServerConfigInfoDto()
    object ServerConfigInfoDto {
      //      implicit val serverConfigInfoDtoCodec: CirceCodec[ServerConfigInfoDto] = {
      //        CirceCodec.from(
      //          Decoder.decodeJson.map(ServerConfigInfoDto.apply),
      //          Encoder.encodeJson.contramap[ServerConfigInfoDto](_.configJson)
      //        )
      //      }
    }

    type UserCategoriesWithProcessingTypesDto = Map[String, String]
    object UserCategoriesWithProcessingTypesDto {
      def apply(map: Map[String, String]): UserCategoriesWithProcessingTypesDto = map
    }
  }
}
