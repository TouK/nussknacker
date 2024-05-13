package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.{
  CannotGenerateStatisticError,
  StatisticError,
  StatisticUrlResponseDto
}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsService

import scala.concurrent.{ExecutionContext, Future}

class StatisticsApiHttpService(
    authenticator: AuthenticationResources,
    usageStatisticsReportsSettingsService: UsageStatisticsReportsSettingsService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator) {

  private val endpoints = new StatisticsApiEndpoints(authenticator.authenticationMethod())

  expose {
    endpoints.statisticUsageEndpoint
      .serverSecurityLogic(authorizeKnownUser[StatisticError])
      .serverLogic { _ => _ =>
        usageStatisticsReportsSettingsService
          .prepareStatisticsUrl()
          .map {
            case Left(_)         => businessError(CannotGenerateStatisticError)
            case Right(maybeUrl) => success(StatisticUrlResponseDto(maybeUrl.toList))
          }
      }
  }

  expose {
    endpoints.registerStatisticsEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogicSuccess { _ => _ =>
        Future.successful(())
      }
  }

}
