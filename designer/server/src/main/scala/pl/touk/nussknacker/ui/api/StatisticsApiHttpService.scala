package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.StatisticError.InvalidURL
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.{StatisticError, StatisticUrlResponseDto}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsDeterminer

import scala.concurrent.ExecutionContext

class StatisticsApiHttpService(
    authenticator: AuthenticationResources,
    determiner: UsageStatisticsReportsSettingsDeterminer
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator)
    with LazyLogging {

  private val endpoints = new StatisticsApiEndpoints(authenticator.authenticationMethod())

  expose {
    endpoints.statisticUsageEndpoint
      .serverSecurityLogic(authorizeKnownUser[StatisticError])
      .serverLogic { _ => _ =>
        determiner
          .determineStatisticsUrl()
          .map(maybeUrl => success(StatisticUrlResponseDto(maybeUrl.toList)))
          .recover {
            case e: IllegalStateException => {
              logger.warn("Statistics URL construction failed", e)
              businessError(InvalidURL)
            }
          }
      }
  }

}
