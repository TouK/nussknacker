package pl.touk.nussknacker.engine.process.compiler

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.flink.api.FlinkEngineRuntimeContext
import pl.touk.nussknacker.engine.flink.util.metrics.FlinkMetricsProviderForScenario
import pl.touk.nussknacker.engine.util.metrics.MetricsProviderForScenario

class FlinkEngineRuntimeContextImpl(val runtimeContext: RuntimeContext) extends FlinkEngineRuntimeContext {
  override def metricsProvider: MetricsProviderForScenario = new FlinkMetricsProviderForScenario(runtimeContext)
}
