package pl.touk.nussknacker.engine.baseengine.api.runtimecontext

import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.util.metrics.{MetricsProvider, NoOpMetricsProvider}

object EngineRuntimeContextPreparer {

  val forTest = new EngineRuntimeContextPreparer(_ => new NoOpMetricsProvider with AutoCloseable {
    override def close(): Unit = {}
  })

}

class EngineRuntimeContextPreparer(metricRegistryForScenario: String => MetricsProvider with AutoCloseable) {
  def prepare(processId: String): BaseEngineRuntimeContext = BaseEngineRuntimeContext(processId, metricRegistryForScenario(processId))
}

case class BaseEngineRuntimeContext(processId: String, metricsProvider: MetricsProvider with AutoCloseable) extends EngineRuntimeContext with AutoCloseable {
  override def close(): Unit = metricsProvider.close()
}