package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.StatisticUrlResponseDto
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsDeterminer

import scala.concurrent.ExecutionContext

class StatisticsApiHttpService(
    authenticator: AuthenticationResources,
    determiner: UsageStatisticsReportsSettingsDeterminer
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator)
    with LazyLogging {

  private val endpoints = new StatisticsApiEndpoints()

  expose {
    endpoints.statisticUrlEndpoint
      .serverLogicSuccess { _ =>
        determiner
          .determineStatisticsUrl()
          .map(maybeUrl => StatisticUrlResponseDto(maybeUrl.toList))
      }
  }

}
