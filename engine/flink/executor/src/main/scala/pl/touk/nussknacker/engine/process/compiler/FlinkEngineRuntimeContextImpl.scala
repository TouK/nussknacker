package pl.touk.nussknacker.engine.process.compiler

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.ComponentUseContextProvider
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, IncContextIdGenerator}
import pl.touk.nussknacker.engine.flink.api.FlinkEngineRuntimeContext
import pl.touk.nussknacker.engine.process.compiler.MetricsProviderForFlink.createMetricsProvider
import pl.touk.nussknacker.engine.util.metrics.MetricsProviderForScenario

case class FlinkEngineRuntimeContextImpl(
    jobData: JobData,
    runtimeContext: RuntimeContext,
    metricsProvider: MetricsProviderForScenario
) extends FlinkEngineRuntimeContext {

  override def contextIdGenerator(nodeId: String): ContextIdGenerator =
    new IncContextIdGenerator(
      jobData.metaData.name.value + "-" + nodeId + "-" + runtimeContext.getTaskInfo.getIndexOfThisSubtask
    )

}

object FlinkEngineRuntimeContextImpl {

//  This creates FlinkEngineRuntimeContextImpl with correct metricsProviderForScenario based on ComponentUseContextProvider
  def apply(
      jobData: JobData,
      runtimeContext: RuntimeContext,
      componentUseContextProvider: ComponentUseContextProvider
  ): FlinkEngineRuntimeContextImpl = {
    val properMetricsProvider = createMetricsProvider(componentUseContextProvider, runtimeContext)
    new FlinkEngineRuntimeContextImpl(jobData, runtimeContext, properMetricsProvider)
  }

}
