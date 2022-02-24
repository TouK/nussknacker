package pl.touk.nussknacker.engine.process.functional

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import org.apache.flink.configuration.{ConfigConstants, Configuration, MetricOptions}
import org.apache.flink.metrics.reporter.AbstractReporter
import org.apache.flink.metrics._

import scala.collection.JavaConverters._

object TestReporterUtil {

  def configWithTestMetrics(c: Configuration = new Configuration(), name: String = UUID.randomUUID().toString): Configuration = {
    c.setString(MetricOptions.REPORTERS_LIST, "test")
    c.setString(ConfigConstants.METRICS_REPORTER_PREFIX + "test.class", classOf[TestReporter].getName)
    c.setString(ConfigConstants.METRICS_REPORTER_PREFIX + "test.name", name)
    c
  }

}

object TestReporter {

  private val instances: ConcurrentHashMap[String, TestReporter] = new ConcurrentHashMap[String, TestReporter]()

  def resetAll(): Unit =
    instances.values().asScala.foreach(_.reset())

  def reset(name: String): Unit =
    get(name).reset()

  def removeAll: Unit =
    instances.clear()

  def remove(name: String): Unit =
    instances.remove(name)

  def append(name: String, reporter: TestReporter): Unit =
    instances.put(name, reporter)

  def get(name: String): TestReporter =
    Option(
      TestReporter
      .instances
      .get(name)
    ).getOrElse(
      throw new IllegalArgumentException("Reporter doesn't exists.")
    )
}

class TestReporter extends AbstractReporter {

  def reset(): Unit = {
    histograms.clear()
    gauges.clear()
    counters.clear()
    meters.clear()
  }

  def testHistograms: Map[Histogram, String] = histograms.asScala.toMap

  def testHistogram(containing: String): Histogram = {
    testHistograms.filter(_._2.contains(containing)).keys.head
  }

  def testGauges(containing: String): Iterable[Gauge[_]] = gauges.asScala.filter(_._2.contains(containing)).keys

  def testCounters(containing: String): Iterable[Counter] = counters.asScala.filter(_._2.contains(containing)).keys

  def names: Iterable[String] = counters.values().asScala

  override def notifyOfRemovedMetric(metric: Metric, metricName: String, group: MetricGroup): Unit = {}

  override def close(): Unit = {}

  override def open(config: MetricConfig): Unit = {
    if (!config.containsKey("name")) {
      throw new IllegalArgumentException("Missing param `name` in configuration.")
    }

    TestReporter.append(config.getString("name", ""), this)
  }

  override def filterCharacters(input: String): String = input
}

