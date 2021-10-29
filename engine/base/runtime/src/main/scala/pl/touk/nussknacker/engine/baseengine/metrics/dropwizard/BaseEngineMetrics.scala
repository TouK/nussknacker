package pl.touk.nussknacker.engine.baseengine.metrics.dropwizard

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.MetricRegistry
import pl.touk.nussknacker.engine.baseengine.metrics.dropwizard.influxdb.BaseEngineInfluxDbReporter
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

import scala.util.control.NonFatal

object BaseEngineMetrics extends LazyLogging {

  def prepareRegistry(config: Config): MetricRegistry = {
    val metricRegistry = new MetricRegistry
    val metricReporters = loadMetricsReporters()
    if (metricReporters.nonEmpty) {
      metricReporters.foreach { reporter =>
        reporter.createAndRunReporter(metricRegistry, config)
      }
    } else {
      BaseEngineInfluxDbReporter.createAndRunReporterIfConfigured(metricRegistry, config)
    }
    metricRegistry
  }

  private def loadMetricsReporters(): List[MetricsReporter] = {
    try {
      val reporters = ScalaServiceLoader.load[MetricsReporter](Thread.currentThread().getContextClassLoader)
      logger.info(s"Loaded metrics reporters: ${reporters.map(_.getClass.getCanonicalName).mkString(", ")}")
      reporters
    } catch {
      case NonFatal(ex) =>
        logger.warn("Metrics reporter load failed. There will be no metrics reporter in standalone", ex)
        List.empty
    }
  }
}
