package pl.touk.nussknacker.ui.api

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.StatisticError.{DbError, InvalidURL}
import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.{StatisticError, StatisticUrlResponseDto}
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsDeterminer

import java.sql.SQLException
import scala.concurrent.ExecutionContext

class StatisticsApiHttpService(
    authenticator: AuthenticationResources,
    determiner: UsageStatisticsReportsSettingsDeterminer
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authenticator)
    with LazyLogging {

  private val endpoints    = new StatisticsApiEndpoints(authenticator.authenticationMethod())
  private val errorMessage = "Statistics URL construction failed"

  expose {
    endpoints.statisticUsageEndpoint
      .serverSecurityLogic(authorizeKnownUser[StatisticError])
      .serverLogic { _ => _ =>
        determiner
          .determineStatisticsUrl()
          .map(maybeUrl => success(StatisticUrlResponseDto(maybeUrl.toList)))
          // TODO: how should we handle JDK and DB errors?
          .recover {
            case e: IllegalStateException => {
              logger.warn(errorMessage, e)
              businessError(InvalidURL)
            }
            case e: SQLException => {
              logger.warn(errorMessage, e)
              businessError(DbError)
            }
          }
      }
  }

}
