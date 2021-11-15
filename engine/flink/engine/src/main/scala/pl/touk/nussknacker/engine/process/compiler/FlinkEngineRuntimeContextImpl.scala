package pl.touk.nussknacker.engine.process.compiler

import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, IncContextIdGenerator}
import pl.touk.nussknacker.engine.flink.api.FlinkEngineRuntimeContext
import pl.touk.nussknacker.engine.flink.util.metrics.FlinkMetricsProviderForScenario
import pl.touk.nussknacker.engine.util.metrics.MetricsProviderForScenario

case class FlinkEngineRuntimeContextImpl(jobData: JobData, runtimeContext: RuntimeContext) extends FlinkEngineRuntimeContext {
  override val metricsProvider: MetricsProviderForScenario = new FlinkMetricsProviderForScenario(runtimeContext)

  override def contextIdGenerator(nodeId: String): ContextIdGenerator =
    new IncContextIdGenerator(jobData.metaData.id + "-" + nodeId + "-" + runtimeContext.getIndexOfThisSubtask)

}