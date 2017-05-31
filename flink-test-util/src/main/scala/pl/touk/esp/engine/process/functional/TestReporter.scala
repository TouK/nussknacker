package pl.touk.esp.engine.process.functional

import org.apache.flink.configuration.{ConfigConstants, Configuration}
import org.apache.flink.metrics.reporter.AbstractReporter
import org.apache.flink.metrics.{Metric, MetricConfig, MetricGroup}
import org.apache.flink.streaming.api.environment.{StreamExecutionEnvironment => JavaEnv}
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

object TestReporterUtil {

  def configWithTestMetrics(c: Configuration = new Configuration()): Configuration = {
    c.setString(ConfigConstants.METRICS_REPORTERS_LIST, "test")
    c.setString(ConfigConstants.METRICS_REPORTER_PREFIX + "test.class", classOf[TestReporter].getName)
    c
  }

  def env(configuration: Configuration = configWithTestMetrics()) =
    new StreamExecutionEnvironment(JavaEnv.createLocalEnvironment(1, configuration))

}

object TestReporter {

  def reset() = instances.clear()

  val instances: ArrayBuffer[TestReporter] = new ArrayBuffer[TestReporter]()

  def taskManagerReporter = TestReporter.instances.find(_.testHistograms.exists(_._2.contains("taskmanager"))).get
}


class TestReporter extends AbstractReporter {

  def testHistograms = histograms.toMap

  def testHistogram(containing: String) = testHistograms.filter(_._2.contains(containing)).keys.head

  def testGauges(containing: String) = gauges.toMap.filter(_._2.contains(containing)).keys

  def testCounters(containing: String) = counters.toMap.filter(_._2.contains(containing)).keys

  override def notifyOfRemovedMetric(metric: Metric, metricName: String, group: MetricGroup) = {}

  override def close() = {}

  override def open(config: MetricConfig) = {
    TestReporter.instances.append(this)
  }

  override def filterCharacters(input: String) = input
}

