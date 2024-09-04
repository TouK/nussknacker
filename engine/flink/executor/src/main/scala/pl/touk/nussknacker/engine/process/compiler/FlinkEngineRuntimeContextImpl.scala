package pl.touk.nussknacker.engine.process.compiler

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, IncContextIdGenerator}
import pl.touk.nussknacker.engine.flink.api.FlinkEngineRuntimeContext
import pl.touk.nussknacker.engine.process.compiler.MetricsProviderForFlink.createMetricsProvider
import pl.touk.nussknacker.engine.util.metrics.MetricsProviderForScenario

case class FlinkEngineRuntimeContextImpl(
    jobData: JobData,
    runtimeContext: RuntimeContext,
    metricsProvider: MetricsProviderForScenario
) extends FlinkEngineRuntimeContext {

  @silent("deprecated")
  override def contextIdGenerator(nodeId: String): ContextIdGenerator =
    new IncContextIdGenerator(jobData.metaData.name.value + "-" + nodeId + "-" + runtimeContext.getIndexOfThisSubtask)

}

object FlinkEngineRuntimeContextImpl {

//  This creates FlinkEngineRuntimeContextImpl with correct metricsProviderForScenario based on ComponentUseCase
  def apply(
      jobData: JobData,
      runtimeContext: RuntimeContext,
      componentUseCase: ComponentUseCase
  ): FlinkEngineRuntimeContextImpl = {
    val properMetricsProvider = createMetricsProvider(componentUseCase, runtimeContext)
    new FlinkEngineRuntimeContextImpl(jobData, runtimeContext, properMetricsProvider)
  }

}
