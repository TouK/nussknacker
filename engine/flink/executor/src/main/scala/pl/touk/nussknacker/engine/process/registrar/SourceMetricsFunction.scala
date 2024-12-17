package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.process.compiler.MetricsProviderForFlink.createMetricsProvider
import pl.touk.nussknacker.engine.util.metrics.common.OneSourceMetrics

private[registrar] class SourceMetricsFunction[T](sourceId: String, componentUseCase: ComponentUseCase)
    extends ProcessFunction[T, T] {

  @transient private var metrics: OneSourceMetrics = _

  override def open(openContext: OpenContext): Unit = {
    metrics = new OneSourceMetrics(sourceId)
    val metricsProvider = createMetricsProvider(componentUseCase, getRuntimeContext)
    metrics.registerOwnMetrics(metricsProvider)
  }

  override def processElement(value: T, ctx: ProcessFunction[T, T]#Context, out: Collector[T]): Unit = {
    metrics.process(ctx.timestamp())
    out.collect(value)
  }

}
