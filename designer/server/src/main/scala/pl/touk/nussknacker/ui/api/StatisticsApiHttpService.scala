package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.{
  CannotGenerateStatisticError => ApiCannotGenerateStatisticError,
  StatisticError,
  StatisticUrlResponseDto
}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.statistics.{CannotGenerateStatisticsError, UsageStatisticsReportsSettingsDeterminer}

import scala.concurrent.ExecutionContext

class StatisticsApiHttpService(
    authenticator: AuthenticationResources,
    determiner: UsageStatisticsReportsSettingsDeterminer
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator) {

  private val endpoints = new StatisticsApiEndpoints(authenticator.authenticationMethod())

  expose {
    endpoints.statisticUsageEndpoint
      .serverSecurityLogic(authorizeKnownUser[StatisticError])
      .serverLogic { _ => _ =>
        determiner
          .prepareStatisticsUrl()
          .map {
            case Left(CannotGenerateStatisticsError) => businessError(ApiCannotGenerateStatisticError)
            case Right(url)                          => success(StatisticUrlResponseDto(url.toList))
          }
      }
  }

}
