package pl.touk.nussknacker.engine.lite.metrics.dropwizard
import com.typesafe.config.Config
import io.dropwizard.metrics5.jmx.JmxReporter
import io.dropwizard.metrics5.{MetricName, MetricRegistry}

class JmxMetricsReporter extends MetricsReporter {
  override def createAndRunReporter(metricRegistry: MetricRegistry, prefix: MetricName, config: Config): Unit = {
    JmxReporter
      .forRegistry(metricRegistry)
      .inDomain("nussknacker")
      .build()
      .start()
  }
}
