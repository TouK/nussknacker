package pl.touk.nussknacker.engine.baseengine.api.runtimecontext

import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.util.metrics.{MetricsProviderForScenario, NoOpMetricsProviderForScenario}

object EngineRuntimeContextPreparer {

  val noOp = new EngineRuntimeContextPreparer(_ => CloseableNoOpMetricsProviderForScenario)

  private object CloseableNoOpMetricsProviderForScenario extends NoOpMetricsProviderForScenario with AutoCloseable {
    override def close(): Unit = {}
  }
}

class EngineRuntimeContextPreparer(metricRegistryForScenario: String => MetricsProviderForScenario with AutoCloseable) {
  def prepare(processId: String): BaseEngineRuntimeContext = BaseEngineRuntimeContext(processId, metricRegistryForScenario(processId))
}

case class BaseEngineRuntimeContext(processId: String, metricsProvider: MetricsProviderForScenario with AutoCloseable) extends EngineRuntimeContext with AutoCloseable {
  override def close(): Unit = metricsProvider.close()
}