package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.process.compiler.MetricsProviderForFlink.createMetricsProvider
import pl.touk.nussknacker.engine.util.metrics.common.OneSourceMetrics

private[registrar] class SourceMetricsFunction[T](
    sourceId: String,
    componentUseCase: ComponentUseCase,
    scenarioName: String
) extends ProcessFunction[T, T] {

  @transient private var metrics: OneSourceMetrics = _
  private val LOG                                  = LoggerFactory.getLogger(classOf[SourceMetricsFunction[T]])

  override def open(parameters: Configuration): Unit = {
    LOG.info(s"-----OPENING SOURCE METRICS with $parameters")
    metrics = new OneSourceMetrics(sourceId, scenarioName)
    val metricsProvider = createMetricsProvider(componentUseCase, getRuntimeContext)
    metrics.registerOwnMetrics(metricsProvider)
  }

  override def processElement(value: T, ctx: ProcessFunction[T, T]#Context, out: Collector[T]): Unit = {

    LOG.info(s"-----PROCESSING ELEMENT $value")
    metrics.process(ctx.timestamp())
    out.collect(value)
  }

}
