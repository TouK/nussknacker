package pl.touk.nussknacker.engine.api.runtimecontext

import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.util.metrics.MetricsProviderForScenario

trait EngineRuntimeContext {

  def jobData: JobData

  def metricsProvider: MetricsProviderForScenario

}
