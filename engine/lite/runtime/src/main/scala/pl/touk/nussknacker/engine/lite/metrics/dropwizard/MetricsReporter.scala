package pl.touk.nussknacker.engine.lite.metrics.dropwizard

import com.typesafe.config.Config
import io.dropwizard.metrics5.{MetricName, MetricRegistry}

trait MetricsReporter {

  def createAndRunReporter(metricRegistry: MetricRegistry, prefix: MetricName, config: Config): Unit

}
