package pl.touk.nussknacker.ui.api

import com.typesafe.config.{Config, ConfigRenderOptions}
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.api.AppResourcesEndpoints.Dtos.HealthCheckProcessResponseDto.Status
import pl.touk.nussknacker.ui.api.AppResourcesEndpoints.Dtos.{BuildInfoDto, HealthCheckProcessResponseDto, ServerConfigInfoDto, ServerConfigInfoErrorDto, UserCategoriesWithProcessingTypesDto}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, LoggedUser}
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.{ExecutionContext, Future}

class AppHttpService(config: Config,
                     modelData: ProcessingTypeDataProvider[ModelData, _],
                     processCategoryService: ProcessCategoryService,
                     exposeConfig: Boolean)
                    (implicit executionContext: ExecutionContext)
  extends LazyLogging {

  def publicServerEndpoints: List[ServerEndpoint[Any, Future]] = List(
    healthCheck, buildInfo
  )

  def securedServerEndpoints(implicit user: LoggedUser): List[ServerEndpoint[Any, Future]] =
    List(
      userCategoriesWithProcessingTypesEndpoint
    ) ::: (
      if (exposeConfig) Some(serverConfigInfo) else None
      ).toList

  private val healthCheck = {
    AppResourcesEndpoints.healthCheckEndpoint
      .serverLogicSuccess { _ =>
        Future.successful(
          HealthCheckProcessResponseDto(
            status = Status.OK,
            message = None,
            processes = None
          )
        )
      }
  }

  private val buildInfo = {
    AppResourcesEndpoints.buildInfoEndpoint
      .serverLogicSuccess { _ =>
        Future {
          // todo: refactor
          //          val configuredBuildInfo = config.getAs[Map[String, String]]("globalBuildInfo").getOrElse(Map())
          //          val globalBuildInfo = (BuildInfo.toMap.mapValuesNow(_.toString) ++ configuredBuildInfo)
          val modelDataInfo: Map[ProcessingType, Map[String, String]] = modelData.all.mapValuesNow(_.configCreator.buildInfo())
          BuildInfoDto(BuildInfo.name, BuildInfo.gitCommit, BuildInfo.buildTime, BuildInfo.version, modelDataInfo)
        }
      }
  }

  private def serverConfigInfo(implicit user: LoggedUser) = {
    AppResourcesEndpoints.serverConfigEndpoint
      .serverSecurityLogicSuccess(Future.successful)
      .serverLogic {
        case _: AdminUser => _ =>
          Future {
            val configJson = parser.parse(config.root().render(ConfigRenderOptions.concise())).left.map(_.message)
            configJson match {
              case Right(json) =>
                Right(ServerConfigInfoDto(json))
              case Left(errorMessage) =>
                logger.error(s"Cannot create JSON from the Nussknacker configuration. Error: $errorMessage")
                throw new Exception("Cannot prepare configuration")
            }
          }
        case _: CommonUser => _ =>
          Future.successful(Left(ServerConfigInfoErrorDto.AuthorizationServerConfigInfoErrorDto))
      }
  }

  private def userCategoriesWithProcessingTypesEndpoint(implicit user: LoggedUser) = {
    AppResourcesEndpoints.userCategoriesWithProcessingTypesEndpoint
      .serverSecurityLogicSuccess(Future.successful)
      .serverLogicSuccess { _ => _ =>
        Future {
          UserCategoriesWithProcessingTypesDto(processCategoryService.getUserCategoriesWithType(user))
        }
      }
  }

}
