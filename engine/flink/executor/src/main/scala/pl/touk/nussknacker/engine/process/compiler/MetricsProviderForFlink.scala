package pl.touk.nussknacker.engine.process.compiler

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.ComponentUseContextProvider
import pl.touk.nussknacker.engine.util.metrics.{MetricsProviderForScenario, NoOpMetricsProviderForScenario}

object MetricsProviderForFlink {

  def createMetricsProvider(
      componentUseContextProvider: ComponentUseContextProvider,
      runtimeContext: RuntimeContext
  ): MetricsProviderForScenario = {
    componentUseContextProvider match {
      case ComponentUseContextProvider.TestRuntime => NoOpMetricsProviderForScenario
      case _                                       => new FlinkMetricsProviderForScenario(runtimeContext)
    }
  }

}
