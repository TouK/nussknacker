package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.{
  CannotGenerateStatisticError,
  StatisticError,
  StatisticUrlResponseDto
}
import pl.touk.nussknacker.ui.security.api.AuthManager
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsDeterminer

import scala.concurrent.ExecutionContext

class StatisticsApiHttpService(
    authManager: AuthManager,
    determiner: UsageStatisticsReportsSettingsDeterminer
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authManager) {

  private val endpoints = new StatisticsApiEndpoints(authManager.authenticationEndpointInput())

  expose {
    endpoints.statisticUsageEndpoint
      .serverSecurityLogic(authorizeKnownUser[StatisticError])
      .serverLogic { _ => _ =>
        determiner
          .prepareStatisticsUrl()
          .map {
            case Left(_)         => businessError(CannotGenerateStatisticError)
            case Right(maybeUrl) => success(StatisticUrlResponseDto(maybeUrl.toList))
          }
      }
  }

}
