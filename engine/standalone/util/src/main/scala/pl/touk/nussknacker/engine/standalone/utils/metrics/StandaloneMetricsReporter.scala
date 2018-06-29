package pl.touk.nussknacker.engine.standalone.utils.metrics

import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config

trait StandaloneMetricsReporter {

  def createAndRunReporter(metricRegistry: MetricRegistry, config: Config): Unit

}
