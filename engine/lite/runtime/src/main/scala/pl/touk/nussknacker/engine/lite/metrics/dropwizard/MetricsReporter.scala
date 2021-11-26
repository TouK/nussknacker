package pl.touk.nussknacker.engine.lite.metrics.dropwizard

import com.typesafe.config.Config
import io.dropwizard.metrics5.MetricRegistry

trait MetricsReporter {

  def createAndRunReporter(metricRegistry: MetricRegistry, config: Config): Unit

}
