package pl.touk.nussknacker.engine.lite.metrics.dropwizard

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.{MetricName, MetricRegistry}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import pl.touk.nussknacker.engine.api.namespaces.Namespace
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.influxdb.LiteEngineInfluxDbReporter
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

class LiteMetricRegistryFactory(defaultInstanceId: => String, namespace: Option[Namespace]) extends LazyLogging {

  import LiteMetricRegistryFactory._

  val metricsConfigPath = "metrics"

  def prepareRegistry(config: Config): MetricRegistry = {
    val metricRegistry = new MetricRegistry
    config.getAs[Config](metricsConfigPath) match {
      case Some(metricConfig) =>
        registerReporters(metricRegistry, metricConfig)
      case None =>
        logger.info("No metrics configuration. Metrics will not be reported!")
    }
    metricRegistry
  }

  private def registerReporters(metricRegistry: MetricRegistry, metricsConfig: Config): Unit = {
    val prefix          = prepareMetricPrefix(metricsConfig.rootAs[CommonMetricConfig], defaultInstanceId, namespace)
    val metricReporters = loadMetricsReporters()
    if (metricReporters.nonEmpty) {
      metricReporters.foreach { reporter =>
        reporter.createAndRunReporter(metricRegistry, prefix, metricsConfig)
      }
    } else {
      LiteEngineInfluxDbReporter.createAndRunReporterIfConfigured(metricRegistry, prefix, metricsConfig)
    }
    new JmxMetricsReporter().createAndRunReporter(metricRegistry, prefix, metricsConfig)
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

object LiteMetricRegistryFactory extends LazyLogging {

  def usingHostnameAsDefaultInstanceId(namespace: Option[Namespace]) =
    new LiteMetricRegistryFactory(hostname, namespace)

  def hostname: String = {
    // Checking COMPUTERNAME to make it works also on windows, see: https://stackoverflow.com/a/33112997/1370301
    sys.env.get("HOSTNAME") orElse sys.env.get("COMPUTERNAME") getOrElse {
      logger.warn("Cannot determine hostname - will be used 'localhost' instead")
      "localhost"
    }
  }

  private[dropwizard] def prepareMetricPrefix(
      conf: CommonMetricConfig,
      defaultInstanceId: => String,
      namespace: Option[Namespace]
  ): MetricName = {
    val metricPrefix = conf.prefix
      .map(MetricName.build(_))
      .getOrElse(MetricName.empty())
      .tagged("instanceId", conf.instanceId.getOrElse(defaultInstanceId))
      .tagged("env", conf.environment)
      .tagged(conf.additionalTags.asJava)
    namespace match {
      case Some(Namespace(value, _)) => metricPrefix.tagged("namespace", value)
      case None                      => metricPrefix
    }
  }

  private[dropwizard] case class CommonMetricConfig(
      prefix: Option[String],
      instanceId: Option[String],
      environment: String,
      additionalTags: Map[String, String] = Map.empty
  )

}
