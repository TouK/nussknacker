package pl.touk.nussknacker.engine.util.runtimecontext

import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext, IncContextIdGenerator}
import pl.touk.nussknacker.engine.api.{JobData, NodeId}
import pl.touk.nussknacker.engine.util.metrics.{MetricsProviderForScenario, NoOpMetricsProviderForScenario}

case class TestEngineRuntimeContext(
    jobData: JobData,
    metricsProvider: MetricsProviderForScenario = NoOpMetricsProviderForScenario
) extends EngineRuntimeContext {
  override def contextIdGenerator(nodeId: NodeId): ContextIdGenerator =
    IncContextIdGenerator.withProcessIdNodeIdPrefix(jobData, nodeId, taskId = 0)
}
