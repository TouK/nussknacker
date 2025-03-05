package pl.touk.nussknacker.engine.process.compiler

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.RuntimeMode
import pl.touk.nussknacker.engine.util.metrics.{MetricsProviderForScenario, NoOpMetricsProviderForScenario}

object MetricsProviderForFlink {

  def createMetricsProvider(
      runtimeMode: RuntimeMode,
      runtimeContext: RuntimeContext
  ): MetricsProviderForScenario = {
    runtimeMode match {
      case RuntimeMode.Test => NoOpMetricsProviderForScenario
      case _                => new FlinkMetricsProviderForScenario(runtimeContext)
    }
  }

}
