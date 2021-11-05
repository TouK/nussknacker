package pl.touk.nussknacker.engine.api.runtimecontext

import pl.touk.nussknacker.engine.util.metrics.MetricsProviderForScenario

trait EngineRuntimeContext {

  def metricsProvider: MetricsProviderForScenario

}