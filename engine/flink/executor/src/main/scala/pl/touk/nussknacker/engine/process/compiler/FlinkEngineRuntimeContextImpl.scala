package pl.touk.nussknacker.engine.process.compiler

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.RuntimeMode
import pl.touk.nussknacker.engine.api.{JobData, NodeId}
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, IncContextIdGenerator}
import pl.touk.nussknacker.engine.flink.api.{FlinkEngineContext, FlinkEngineRuntimeContext, RuntimeCtx}
import pl.touk.nussknacker.engine.process.compiler.MetricsProviderForFlink.createMetricsProvider
import pl.touk.nussknacker.engine.util.metrics.MetricsProviderForScenario

case class FlinkEngineRuntimeContextImpl(
    jobData: JobData,
    runtimeContext: FlinkEngineContext,
    metricsProvider: MetricsProviderForScenario
) extends FlinkEngineRuntimeContext {

  override def contextIdGenerator(nodeId: NodeId): ContextIdGenerator =
    new IncContextIdGenerator(
      jobData.metaData.name,
      nodeId,
      runtimeContext.getTaskInfo.getIndexOfThisSubtask.toLong,
    )

}

object FlinkEngineRuntimeContextImpl {

//  This creates FlinkEngineRuntimeContextImpl with correct metricsProviderForScenario based on ComponentUseContextProvider
  def apply(
      jobData: JobData,
      runtimeContext: FlinkEngineContext,
      runtimeMode: RuntimeMode
  ): FlinkEngineRuntimeContextImpl = {
    val properMetricsProvider = createMetricsProvider(runtimeMode, runtimeContext)
    new FlinkEngineRuntimeContextImpl(jobData, runtimeContext, properMetricsProvider)
  }

}
