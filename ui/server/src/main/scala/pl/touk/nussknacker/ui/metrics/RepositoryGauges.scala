package pl.touk.nussknacker.ui.metrics

import io.dropwizard.metrics5.{CachedGauge, Gauge, MetricName, MetricRegistry}
import pl.touk.nussknacker.ui.process.repository.DBFetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class RepositoryGauges(metricRegistry: MetricRegistry,
                       processRepository: DBFetchingProcessRepository[Future]) {

  private val cacheLengthSeconds = 5

  private val awaitTime = 5 seconds

  def prepareGauges(): Unit = {
    val globalGauge = new GlobalGauge
    metricRegistry.register(MetricName.build("scenarios", "count"), globalGauge.derivative(_.scenarios))
    metricRegistry.register(MetricName.build("fragments", "count"), globalGauge.derivative(_.fragments))
    metricRegistry.register(MetricName.build("deployedScenarios", "count"), globalGauge.derivative(_.deployedScenarios))

  }

  private class GlobalGauge extends CachedGauge[Values](cacheLengthSeconds, TimeUnit.SECONDS) {
    override def loadValue(): Values = {
      implicit val user: LoggedUser = NussknackerInternalUser
      val result = processRepository.fetchProcesses[Unit](
        isSubprocess = None,
        isArchived = Some(false),
        isDeployed = None,
        categories = None,
        processingTypes = None
      ).map { scenarios =>
        val all = scenarios.size
        val deployed = scenarios.count(_.isDeployed)
        val fragments = scenarios.count(_.isSubprocess)
        Values(all, deployed, fragments)
      }
      Await.result(result, awaitTime)
    }

    def derivative(transform: Values => Long): Gauge[Long] = () => transform(getValue)
  }

  private case class Values(scenarios: Long, deployedScenarios: Long, fragments: Long)

}
