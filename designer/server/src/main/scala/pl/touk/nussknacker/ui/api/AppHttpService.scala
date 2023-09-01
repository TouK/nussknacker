package pl.touk.nussknacker.ui.api

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.api.AppResourcesEndpoints.Dtos.HealthCheckProcessResponseDto.Status
import pl.touk.nussknacker.ui.api.AppResourcesEndpoints.Dtos.{BuildInfoDto, HealthCheckProcessResponseDto}
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.{ExecutionContext, Future}

class AppHttpService(config: Config,
                     modelData: ProcessingTypeDataProvider[ModelData, _])
                    (implicit executionContext: ExecutionContext) {

  def serverEndpoints: List[ServerEndpoint[Any, Future]] = List(
    healthCheck, buildInfo
  )

  def serverEndpoint2(implicit loggedUser: LoggedUser): ServerEndpoint[Any, Future] = {
    AppResourcesEndpoints.healthCheckEndpoint2
      .serverSecurityLogicSuccess[LoggedUser, Future](Future.successful)
      .serverLogic { loggedUser =>
        _ =>
          Future.successful(Right(HealthCheckProcessResponseDto(Status.OK, Some(s"logged user: $loggedUser"), None)))
      }
  }

  private val healthCheck = {
    AppResourcesEndpoints.healthCheckEndpoint
      .serverLogic[Future] { _ =>
        Future.successful(Right(
          HealthCheckProcessResponseDto(
            status = Status.OK,
            message = None,
            processes = None
          )
        ))
      }
  }

  private val buildInfo = {
    AppResourcesEndpoints.buildInfoEndpoint
      .serverLogic[Future] { _ =>
        Future(Right {
          // todo: refactor
          val configuredBuildInfo = config.getAs[Map[String, String]]("globalBuildInfo").getOrElse(Map())
          val globalBuildInfo = (BuildInfo.toMap.mapValuesNow(_.toString) ++ configuredBuildInfo)
          val modelDataInfo: Map[ProcessingType, Map[String, String]] = modelData.all.mapValuesNow(_.configCreator.buildInfo())

          BuildInfoDto(globalBuildInfo, modelDataInfo)
        })
      }
  }
}
