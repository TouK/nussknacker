package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.process.compiler.FlinkMetricsProviderForScenario
import pl.touk.nussknacker.engine.util.metrics.NoOpMetricsProviderForScenario
import pl.touk.nussknacker.engine.util.metrics.common.OneSourceMetrics

private[registrar] class SourceMetricsFunction[T](sourceId: String) extends ProcessFunction[T, T] {

  @transient private var metrics: OneSourceMetrics = _

  override def open(parameters: Configuration): Unit = {
    metrics = new OneSourceMetrics(sourceId)
    val metricsProvider = new FlinkMetricsProviderForScenario(getRuntimeContext)
    metrics.registerOwnMetrics(metricsProvider)
  }

  override def processElement(value: T, ctx: ProcessFunction[T, T]#Context, out: Collector[T]): Unit = {
    metrics.process(ctx.timestamp())
    out.collect(value)
  }

}

private[registrar] class TestSourceMetricsFunction[T](sourceId: String) extends ProcessFunction[T, T] {

  @transient private var metrics: OneSourceMetrics = _

  override def open(parameters: Configuration): Unit = {
    metrics = new OneSourceMetrics(sourceId)
    val metricsProvider = NoOpMetricsProviderForScenario
    metrics.registerOwnMetrics(metricsProvider)
  }

  override def processElement(value: T, ctx: ProcessFunction[T, T]#Context, out: Collector[T]): Unit = {
    metrics.process(ctx.timestamp())
    out.collect(value)
  }

}
