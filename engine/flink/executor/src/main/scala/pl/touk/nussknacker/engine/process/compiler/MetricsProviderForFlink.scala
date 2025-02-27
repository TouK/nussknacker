package pl.touk.nussknacker.engine.process.compiler

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.ComponentUseCase
import pl.touk.nussknacker.engine.util.metrics.{MetricsProviderForScenario, NoOpMetricsProviderForScenario}

object MetricsProviderForFlink {

  def createMetricsProvider(
      componentUseCase: ComponentUseCase,
      runtimeContext: RuntimeContext
  ): MetricsProviderForScenario = {
    componentUseCase match {
      case ComponentUseCase.TestRuntime => NoOpMetricsProviderForScenario
      case _                            => new FlinkMetricsProviderForScenario(runtimeContext)
    }
  }

}
