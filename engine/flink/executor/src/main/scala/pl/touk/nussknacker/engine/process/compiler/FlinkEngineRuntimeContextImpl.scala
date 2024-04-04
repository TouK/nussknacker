package pl.touk.nussknacker.engine.process.compiler

import org.apache.flink.api.common.functions.util.AbstractRuntimeUDFContext
import org.apache.flink.api.common.functions.{IterationRuntimeContext, RuntimeContext}
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, IncContextIdGenerator}
import pl.touk.nussknacker.engine.flink.api.FlinkEngineRuntimeContext
import pl.touk.nussknacker.engine.process.compiler.FlinkEngineRuntimeContextImpl.setupMetricsProvider
import pl.touk.nussknacker.engine.util.metrics.{MetricsProviderForScenario, NoOpMetricsProviderForScenario}

case class FlinkEngineRuntimeContextImpl(jobData: JobData, runtimeContext: RuntimeContext)
    extends FlinkEngineRuntimeContext {
  override val metricsProvider: MetricsProviderForScenario = setupMetricsProvider(runtimeContext)

  override def contextIdGenerator(nodeId: String): ContextIdGenerator =
    new IncContextIdGenerator(jobData.metaData.id + "-" + nodeId + "-" + runtimeContext.getIndexOfThisSubtask)

}

object FlinkEngineRuntimeContextImpl {

  def setupMetricsProvider(runtimeContext: RuntimeContext): MetricsProviderForScenario = {
    runtimeContext match {
      case _: AbstractRuntimeUDFContext => NoOpMetricsProviderForScenario
      case _: IterationRuntimeContext   => NoOpMetricsProviderForScenario
    }
  }

}
