package pl.touk.nussknacker.engine.api.runtimecontext

import pl.touk.nussknacker.engine.util.metrics.MetricsProvider

trait EngineRuntimeContext {

  def metricsProvider: MetricsProvider

}