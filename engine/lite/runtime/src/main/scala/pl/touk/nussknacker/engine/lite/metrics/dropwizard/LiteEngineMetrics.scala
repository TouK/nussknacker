package pl.touk.nussknacker.engine.lite.metrics.dropwizard

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.{MetricName, MetricRegistry}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.influxdb.LiteEngineInfluxDbReporter
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

import scala.jdk.CollectionConverters.mapAsJavaMapConverter
import scala.util.control.NonFatal

object LiteEngineMetrics extends LazyLogging {

  val metricsConfigPath = "metrics"

  case class CommonMetricConfig(prefix: Option[String],
                                host: String,
                                environment: String,
                                additionalTags: Map[String, String] = Map.empty)

  def prepareRegistry(config: Config): MetricRegistry = {
    val metricRegistry = new MetricRegistry
    def prefix() = preparePrefix(config.as[CommonMetricConfig](metricsConfigPath))
    val metricReporters = loadMetricsReporters()
    if (metricReporters.nonEmpty) {
      metricReporters.foreach { reporter =>
        reporter.createAndRunReporter(metricRegistry, prefix(), config.getConfig(metricsConfigPath))
      }
    } else {
      LiteEngineInfluxDbReporter.createAndRunReporterIfConfigured(metricRegistry, prefix, config)
    }
    metricRegistry
  }

  private def preparePrefix(conf: CommonMetricConfig): MetricName = {
    conf.prefix.map(MetricName.build(_)).getOrElse(MetricName.empty())
      .tagged("host", conf.host)
      .tagged("env", conf.environment)
      .tagged(conf.additionalTags.asJava)
  }

  private def loadMetricsReporters(): List[MetricsReporter] = {
    try {
      val reporters = ScalaServiceLoader.load[MetricsReporter](Thread.currentThread().getContextClassLoader)
      logger.info(s"Loaded metrics reporters: ${reporters.map(_.getClass.getCanonicalName).mkString(", ")}")
      reporters
    } catch {
      case NonFatal(ex) =>
        logger.warn("Metrics reporter load failed. There will be no metrics reporter", ex)
        List.empty
    }
  }
}
