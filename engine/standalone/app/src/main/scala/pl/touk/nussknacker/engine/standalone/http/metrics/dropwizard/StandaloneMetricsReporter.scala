package pl.touk.nussknacker.engine.standalone.http.metrics.dropwizard

import com.typesafe.config.Config
import io.dropwizard.metrics5.MetricRegistry

trait StandaloneMetricsReporter {

  def createAndRunReporter(metricRegistry: MetricRegistry, config: Config): Unit

}
