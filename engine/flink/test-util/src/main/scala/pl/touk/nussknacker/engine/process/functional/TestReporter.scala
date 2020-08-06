package pl.touk.nussknacker.engine.process.functional

import org.apache.flink.configuration.{ConfigConstants, Configuration, MetricOptions}
import org.apache.flink.metrics.reporter.AbstractReporter
import org.apache.flink.metrics.{Metric, MetricConfig, MetricGroup}
import org.apache.flink.streaming.api.environment.{StreamExecutionEnvironment => JavaEnv}
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

object TestReporterUtil {

  def configWithTestMetrics(c: Configuration = new Configuration()): Configuration = {
    c.setString(MetricOptions.REPORTERS_LIST, "test")
    c.setString(ConfigConstants.METRICS_REPORTER_PREFIX + "test.class", classOf[TestReporter].getName)
    c
  }

}

object TestReporter {

  val taskManagerHistogramName = "taskmanager"

  private val instances: ArrayBuffer[TestReporter] = new ArrayBuffer[TestReporter]()

  def reset() = synchronized {
    instances.foreach(_.reset())
  }

  def append(reporter: TestReporter): Unit = synchronized {
    instances.append(reporter)
  }

  def headReporter: TestReporter = synchronized {
    instances.head
  }

  def findReporter(p: TestReporter => Boolean): TestReporter = synchronized {
    TestReporter
      .instances
      .find(p)
      .getOrElse(throw new IllegalArgumentException("Reporter doesn't exists."))
  }

  def taskManagerReporter: TestReporter = findReporter(_.testHistograms.exists(_._2.contains(taskManagerHistogramName)))
}

class TestReporter extends AbstractReporter {

  def reset(): Unit = {
    histograms.clear()
    gauges.clear()
    counters.clear()
    meters.clear()
  }

  def testHistograms = histograms.asScala.toMap

  def testHistogram(containing: String) = testHistograms.filter(_._2.contains(containing)).keys.head

  def testGauges(containing: String) = gauges.asScala.filter(_._2.contains(containing)).keys

  def testCounters(containing: String) = counters.asScala.filter(_._2.contains(containing)).keys

  def names = counters.values().asScala

  override def notifyOfRemovedMetric(metric: Metric, metricName: String, group: MetricGroup) = {}

  override def close() = {}

  override def open(config: MetricConfig) = {
    TestReporter.append(this)
  }

  override def filterCharacters(input: String) = input
}

