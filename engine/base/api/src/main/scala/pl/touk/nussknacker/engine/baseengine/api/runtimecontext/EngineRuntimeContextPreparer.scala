package pl.touk.nussknacker.engine.baseengine.api.runtimecontext

import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext, IncContextIdGenerator}
import pl.touk.nussknacker.engine.util.metrics.{MetricsProviderForScenario, NoOpMetricsProviderForScenario}

object EngineRuntimeContextPreparer {

  val noOp = new EngineRuntimeContextPreparer(_ => CloseableNoOpMetricsProviderForScenario)

  private object CloseableNoOpMetricsProviderForScenario extends NoOpMetricsProviderForScenario with AutoCloseable {
    override def close(): Unit = {}
  }
}

class EngineRuntimeContextPreparer(metricRegistryForScenario: String => MetricsProviderForScenario with AutoCloseable) {
  def prepare(jobData: JobData): BaseEngineRuntimeContext = BaseEngineRuntimeContext(jobData, metricRegistryForScenario(jobData.metaData.id))
}

case class BaseEngineRuntimeContext(jobData: JobData,
                                    metricsProvider: MetricsProviderForScenario with AutoCloseable) extends EngineRuntimeContext with AutoCloseable {

  override def contextIdGenerator(nodeId: String): ContextIdGenerator = IncContextIdGenerator.withProcessIdNodeIdPrefix(jobData, nodeId)

  override def close(): Unit = metricsProvider.close()

}