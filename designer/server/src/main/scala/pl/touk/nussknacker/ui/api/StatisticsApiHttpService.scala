package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.{
  CannotGenerateStatisticError,
  StatisticError,
  StatisticUrlResponseDto
}
import pl.touk.nussknacker.ui.db.timeseries.StatisticsDb
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsService

import scala.concurrent.{ExecutionContext, Future}

class StatisticsApiHttpService(
    authenticator: AuthenticationResources,
    usageStatisticsReportsSettingsService: UsageStatisticsReportsSettingsService,
    db: StatisticsDb[Future]
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator)
    with LazyLogging {

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
      .serverLogicSuccess { _ => request =>
        // todo change to groupMapReduce in scala 2.13
        val groupedByName = request.statistics
          .groupBy(_.name.entryName)
          .map { case (k, v) =>
            k -> v.size.toLong
          }
        db.write(groupedByName)
        Future.successful(())
      }
  }

}
