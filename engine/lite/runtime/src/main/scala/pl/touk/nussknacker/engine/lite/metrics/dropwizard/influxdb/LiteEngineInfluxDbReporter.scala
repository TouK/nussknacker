package pl.touk.nussknacker.engine.lite.metrics.dropwizard.influxdb

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.{MetricName, MetricRegistry}
import io.dropwizard.metrics5.influxdb.InfluxDbReporter

import java.util.concurrent.TimeUnit

object LiteEngineInfluxDbReporter extends LazyLogging {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def createAndRunReporterIfConfigured(metricRegistry: MetricRegistry, prefix: MetricName, config: Config): Option[InfluxDbReporter] = {
    config.getAs[InfluxSenderConfig]("influx").map { influxSenderConfig =>
      logger.info("Found Influxdb metrics reporter config, starting reporter")
      val reporter = InfluxDbHttpReporter.build(metricRegistry, prefix, influxSenderConfig)
      reporter.start(influxSenderConfig.reporterPolling.toSeconds, TimeUnit.SECONDS)
      reporter
    } orElse {
      logger.info("Influxdb metrics reporter config not found")
      None
    }
  }

}
