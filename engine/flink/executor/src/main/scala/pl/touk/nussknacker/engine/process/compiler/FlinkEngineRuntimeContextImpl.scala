package pl.touk.nussknacker.engine.process.compiler

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, IncContextIdGenerator}
import pl.touk.nussknacker.engine.flink.api.FlinkEngineRuntimeContext
import pl.touk.nussknacker.engine.util.metrics.{MetricsProviderForScenario, NoOpMetricsProviderForScenario}

case class FlinkEngineRuntimeContextImpl(
    jobData: JobData,
    runtimeContext: RuntimeContext,
    metricsProvider: MetricsProviderForScenario
) extends FlinkEngineRuntimeContext {

  override def contextIdGenerator(nodeId: String): ContextIdGenerator =
    new IncContextIdGenerator(jobData.metaData.id + "-" + nodeId + "-" + runtimeContext.getIndexOfThisSubtask)

}

object FlinkEngineRuntimeContextImpl {

  def setupMetricsProvider(
      componentUseCase: ComponentUseCase,
      runtimeContext: RuntimeContext
  ): MetricsProviderForScenario = {
    componentUseCase match {
      case ComponentUseCase.TestRuntime => NoOpMetricsProviderForScenario
      case _                            => new FlinkMetricsProviderForScenario(runtimeContext)
    }
  }

  def withProperMetricsProvider(
      jobData: JobData,
      runtimeContext: RuntimeContext,
      componentUseCase: ComponentUseCase
  ): FlinkEngineRuntimeContextImpl = {
    val properMetricsProvider = setupMetricsProvider(componentUseCase, runtimeContext)
    new FlinkEngineRuntimeContextImpl(jobData, runtimeContext, properMetricsProvider)
  }

}
