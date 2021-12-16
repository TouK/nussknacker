package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.flink.util.metrics.FlinkMetricsProviderForScenario
import pl.touk.nussknacker.engine.util.metrics.common.OneSourceMetrics

private[registrar] class SourceMetricsFunction[T](sourceId: String) extends ProcessFunction[T, T] {

  @transient private var metrics: OneSourceMetrics = _

  override def open(parameters: Configuration): Unit = {
    val metricsProvider = new FlinkMetricsProviderForScenario(getRuntimeContext)
    metrics = new OneSourceMetrics(metricsProvider, sourceId)
  }

  override def processElement(value: T, ctx: ProcessFunction[T, T]#Context, out: Collector[T]): Unit = {
    metrics.process(ctx.timestamp())
    out.collect(value)
  }

}
