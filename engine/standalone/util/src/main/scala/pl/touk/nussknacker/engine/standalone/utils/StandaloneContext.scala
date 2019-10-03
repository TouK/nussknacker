package pl.touk.nussknacker.engine.standalone.utils

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.standalone.utils.metrics.MetricsProvider
import pl.touk.nussknacker.engine.util.service.EspTimer

case class StandaloneContext(processId: String, metricsProvider: MetricsProvider) extends LazyLogging {

  def espTimer(instantTimerWindowInSeconds: Long, name: String*): EspTimer = {
    metricsProvider.espTimer(processId, instantTimerWindowInSeconds, name:_*)
  }


  def close(): Unit = {
    metricsProvider.close(processId)
  }

}

class StandaloneContextPreparer(metricRegistry: MetricsProvider) {
  def prepare(processId: String) = StandaloneContext(processId, metricRegistry)
}
