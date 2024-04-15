package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.StatisticError.{
  DbError => ApiDbError,
  InvalidURL
}
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.{StatisticError, StatisticUrlResponseDto}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.statistics.{DbError, UrlError, UsageStatisticsReportsSettingsDeterminer}

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
          .determineStatisticsUrl()
          .map {
            case Left(error) =>
              error match {
                case DbError  => businessError(ApiDbError)
                case UrlError => businessError(InvalidURL)
              }
            case Right(url) => success(StatisticUrlResponseDto(url.toList))
          }
      }
  }

}
