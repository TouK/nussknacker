package pl.touk.nussknacker.ui.metrics

import io.dropwizard.metrics5.{CachedGauge, Gauge, MetricName, MetricRegistry}
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.process.repository.DBFetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

import java.time.Duration
import java.util.concurrent.TimeUnit
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration.DurationInt

class RepositoryGauges(
    metricRegistry: MetricRegistry,
    repositoryGaugesCacheDuration: Duration,
    processRepository: DBFetchingProcessRepository[Future]
) {

  private val awaitTime = 5 seconds

  def prepareGauges(): Unit = {
    val globalGauge = new GlobalGauge
    metricRegistry.register(MetricName.build("scenarios", "count"), globalGauge.derivative(_.scenarios))
    metricRegistry.register(MetricName.build("fragments", "count"), globalGauge.derivative(_.fragments))
    metricRegistry.register(MetricName.build("deployedScenarios", "count"), globalGauge.derivative(_.deployedScenarios))

  }

  private class GlobalGauge extends CachedGauge[Values](repositoryGaugesCacheDuration.toSeconds, TimeUnit.SECONDS) {

    override def loadValue(): Values = {
      implicit val user: LoggedUser = NussknackerInternalUser.instance
      val result =
        processRepository.fetchLatestProcessesDetails[Unit](ScenarioQuery(isArchived = Some(false))).map { scenarios =>
          val all       = scenarios.size
          val deployed  = scenarios.count(_.lastStateAction.exists(_.actionName == ScenarioActionName.Deploy))
          val fragments = scenarios.count(_.isFragment)
          Values(all, deployed, fragments)
        }
      Await.result(result, awaitTime)
    }

    def derivative(transform: Values => Long): Gauge[Long] = () => transform(getValue)
  }

  private sealed case class Values(scenarios: Long, deployedScenarios: Long, fragments: Long)

}
