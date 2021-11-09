package pl.touk.nussknacker.engine.util.metrics

import pl.touk.nussknacker.engine.api.Lifecycle
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext

trait WithMetrics extends Lifecycle {

  @transient protected var metricsProvider: MetricsProviderForScenario = _

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    this.metricsProvider = context.metricsProvider
  }

}