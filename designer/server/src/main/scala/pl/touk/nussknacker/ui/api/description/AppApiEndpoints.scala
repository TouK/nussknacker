package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import enumeratum.EnumEntry.Uppercase
import enumeratum._
import io.circe.syntax.EncoderOps
import io.circe.{Codec => CirceCodec, Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.CirceUtil.HCursorExt
import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import sttp.model.StatusCode.{InternalServerError, NoContent, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.codec.enumeratum._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

class AppApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

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

  lazy val processDeploymentHealthCheckEndpoint
      : SecuredEndpoint[Unit, HealthCheckProcessErrorResponseDto, HealthCheckProcessSuccessResponseDto, Any] =
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

  lazy val processValidationHealthCheckEndpoint
      : SecuredEndpoint[Unit, HealthCheckProcessErrorResponseDto, HealthCheckProcessSuccessResponseDto, Any] =
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
                  version = "1.234.0",
                  processingType = Map(
                    "streaming" -> ModelInfo.fromMap(
                      Map(
                        "process-version" -> "0.1",
                        "engine-version"  -> "0.2",
                        "generation-time" -> "2023-09-25T09:26:30.402299"
                      )
                    )
                  )
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
                summary = Some("Server configuration response"),
                value = ServerConfigInfoDto(
                  Json.obj(
                    "environment" -> Json.fromString("local"),
                    "scenarioTypes" -> Json.obj(
                      "development-tests" -> Json.obj(
                        "type" -> Json.fromString("development-tests")
                      ),
                      "modelConfig" -> Json.obj(
                        "classPath" -> Json.arr(
                          Json.fromString("model/devModel.jar"),
                          Json.fromString("model/flinkExecutor.jar"),
                          Json.fromString("components/flink")
                        )
                      )
                    )
                  )
                )
              )
            )
        )
      )

  lazy val userCategoriesWithProcessingTypesEndpoint
      : SecuredEndpoint[Unit, Unit, UserCategoriesWithProcessingTypesDto, Any] =
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
                    "Category2" -> "streaming2"
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

    private implicit val modelInfoSchema: Schema[ModelInfo] = Schema.schemaForMap[String].as[ModelInfo]

    @derive(encoder, decoder, schema)
    final case class HealthCheckProcessSuccessResponseDto private (
        status: HealthCheckProcessSuccessResponseDto.Status,
        message: Option[String],
        processes: Option[Set[String]]
    )

    object HealthCheckProcessSuccessResponseDto {

      sealed trait Status extends EnumEntry with Uppercase

      object Status extends Enum[Status] with CirceEnum[Status] {
        case object Ok extends Status

        override def values = findValues
      }

      def apply() = new HealthCheckProcessSuccessResponseDto(status = Status.Ok, message = None, processes = None)
    }

    @derive(encoder, decoder, schema)
    final case class HealthCheckProcessErrorResponseDto private (
        status: HealthCheckProcessErrorResponseDto.Status,
        message: Option[String],
        processes: Option[Set[String]]
    )

    object HealthCheckProcessErrorResponseDto {

      sealed trait Status extends EnumEntry with Uppercase

      object Status extends Enum[Status] with CirceEnum[Status] {
        case object Error extends Status

        override def values = findValues
      }

      def apply(message: Option[String], processes: Option[Set[String]]) =
        new HealthCheckProcessErrorResponseDto(status = Status.Error, message, processes)
    }

    @derive(schema)
    final case class BuildInfoDto(
        name: String,
        gitCommit: String,
        buildTime: String,
        version: String,
        processingType: Map[String, ModelInfo],
        globalBuildInfo: Option[Map[String, String]] = None
    )

    object BuildInfoDto {

      implicit val circeCodec: CirceCodec[BuildInfoDto] = {
        CirceCodec.from(
          Decoder.instance { c =>
            for {
              name               <- c.downField("name").as[String]
              version            <- c.downField("version").as[String]
              buildTime          <- c.downField("buildTime").as[String]
              gitCommit          <- c.downField("gitCommit").as[String]
              processingType     <- c.downField("processingType").as[Map[String, ModelInfo]]
              globalBuildInfoOpt <- c.downField("globalBuildInfo").as[Option[Map[String, String]]]
              globalBuildInfo <- globalBuildInfoOpt match {
                case globalBuildInfo @ Some(_) =>
                  Right(globalBuildInfo)
                case None =>
                  // for the purpose of backward compatibility
                  c.toMapExcluding("name", "version", "buildTime", "gitCommit", "processingType")
                    .map {
                      case m if m.isEmpty => None
                      case m              => Some(m)
                    }
              }
            } yield BuildInfoDto(name, gitCommit, buildTime, version, processingType, globalBuildInfo)
          },
          Encoder.encodeJson.contramap { buildInfo =>
            buildInfo.globalBuildInfo.asJson // for the purpose of backward compatibility
              .deepMerge {
                Json
                  .obj(
                    "name"            -> Json.fromString(buildInfo.name),
                    "version"         -> Json.fromString(buildInfo.version),
                    "buildTime"       -> Json.fromString(buildInfo.buildTime),
                    "gitCommit"       -> Json.fromString(buildInfo.gitCommit),
                    "processingType"  -> buildInfo.processingType.asJson,
                    "globalBuildInfo" -> buildInfo.globalBuildInfo.asJson
                  )
              }
          }
        )
      }

    }

    final case class ServerConfigInfoDto(configJson: Json)

    object ServerConfigInfoDto {

      implicit val schema: Schema[ServerConfigInfoDto] = Schema
        .anyObject[Json]
        .map[ServerConfigInfoDto](json => Some(ServerConfigInfoDto(json)))(_.configJson)

      implicit val circeCodec: CirceCodec[ServerConfigInfoDto] = {
        CirceCodec.from(
          Decoder.decodeJson.map(ServerConfigInfoDto.apply),
          Encoder.encodeJson.contramap[ServerConfigInfoDto](_.configJson)
        )
      }

    }

    type UserCategoriesWithProcessingTypesDto = Map[String, String]

    object UserCategoriesWithProcessingTypesDto {
      def apply(map: Map[String, String]): UserCategoriesWithProcessingTypesDto = map
    }

  }

}
