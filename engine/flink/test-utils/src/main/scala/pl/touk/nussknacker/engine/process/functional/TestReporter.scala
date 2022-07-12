package pl.touk.nussknacker.engine.process.functional

import org.apache.flink.configuration.{ConfigConstants, Configuration, MetricOptions}
import org.apache.flink.metrics._
import org.apache.flink.metrics.reporter.MetricReporter
import org.apache.flink.runtime.metrics.scope.ScopeFormat
import pl.touk.nussknacker.engine.api.process.ProcessName

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._

object TestReporterUtil {

  def configWithTestMetrics(name: String, c: Configuration = new Configuration()): Configuration = {
    c.setString(MetricOptions.REPORTERS_LIST, "test")
    c.setString(ConfigConstants.METRICS_REPORTER_PREFIX + "test.class", classOf[TestReporter].getName)
    c.setString(ConfigConstants.METRICS_REPORTER_PREFIX + "test.name", name)
    c
  }

}

object TestReporter {

  private val instances: ConcurrentHashMap[String, TestReporter] = new ConcurrentHashMap[String, TestReporter]()

  def remove(name: String): Unit =
    instances.remove(name)

  def add(name: String, reporter: TestReporter): Unit =
    instances.put(name, reporter)

  def get(name: String): TestReporter = TestReporter.instances.asScala
    .getOrElse(name, throw new IllegalArgumentException("Reporter doesn't exists."))

}

class TestReporter extends MetricReporter with CharacterFilter {

  private val processToMetric = new ConcurrentHashMap[(ProcessName, String), Metric]()

  def testMetrics[T<:Metric](metricNamePattern: String)(implicit scenarioName: ProcessName): Iterable[T] =
    processToMetric.asScala.filterKeys { case (scenarioName1, metricName1) =>
      scenarioName1 == scenarioName && metricName1.contains(metricNamePattern)
    }.values.map(_.asInstanceOf[T])

  override def notifyOfAddedMetric(metric: Metric, metricName: String, group: MetricGroup): Unit = {
    val metricId = group.getMetricIdentifier(metricName, this)
    processToMetric.put((ProcessName(group.getAllVariables.get(ScopeFormat.SCOPE_JOB_NAME)), metricId), metric)
  }

  override def notifyOfRemovedMetric(metric: Metric, metricName: String, group: MetricGroup): Unit = {}

  override def close(): Unit = {}

  override def open(config: MetricConfig): Unit = {
    if (!config.containsKey("name")) {
      throw new IllegalArgumentException("Missing param `name` in configuration.")
    }
    TestReporter.add(config.getString("name", ""), this)
  }

  override def filterCharacters(input: String): String = input
}

