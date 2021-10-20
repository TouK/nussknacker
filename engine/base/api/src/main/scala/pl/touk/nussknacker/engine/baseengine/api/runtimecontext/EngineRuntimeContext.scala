package pl.touk.nussknacker.engine.baseengine.api.runtimecontext

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.baseengine.api.metrics.MetricsProvider
import pl.touk.nussknacker.engine.util.service.EspTimer

case class EngineRuntimeContext(processId: String, metricsProvider: MetricsProvider) extends LazyLogging {

  def espTimer(instantTimerWindowInSeconds: Long, tags: Map[String, String], name: NonEmptyList[String]): EspTimer = {
    metricsProvider.espTimer(processId, instantTimerWindowInSeconds, tags, name)
  }

  def close(): Unit = {
    metricsProvider.close(processId)
  }

}

class EngineRuntimeContextPreparer(metricRegistry: MetricsProvider) {
  def prepare(processId: String): EngineRuntimeContext = EngineRuntimeContext(processId, metricRegistry)
}
