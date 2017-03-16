package pl.touk.esp.engine.process.functional

import java.util.Date

import org.apache.flink.configuration.{ConfigConstants, Configuration}
import org.apache.flink.metrics.reporter.AbstractReporter
import org.apache.flink.metrics.{Metric, MetricConfig, MetricGroup}
import org.apache.flink.streaming.api.environment.{StreamExecutionEnvironment => JavaEnv}
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.process.ProcessTestHelpers.{MockService, SimpleRecord, processInvoker}
import pl.touk.esp.engine.spel

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

class MetricsSpec extends FlatSpec with Matchers with Eventually {


  override implicit val patienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(100, Millis)
  )

  it should "measure time for service" in {
    TestReporter.reset()

    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .processor("proc2", "logService", "all" -> "#input.value2")
      .sink("out", "monitor")
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    val config = new Configuration()
    config.setString(ConfigConstants.METRICS_REPORTERS_LIST, "test")
    config.setString(ConfigConstants.METRICS_REPORTER_PREFIX + "test.class", classOf[TestReporter].getName)


    val env = new StreamExecutionEnvironment(JavaEnv.createLocalEnvironment(1, config))

    processInvoker.invoke(process, data, env)

    MockService.data shouldNot be('empty)


    val histogram = TestReporter.taskManagerReporter.testHistogram("serviceTimes.mockService.OK")
    histogram.getCount shouldBe 1

  }

  it should "measure errors" in {
    TestReporter.reset()

    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .processor("proc2", "logService", "all" -> "1 / #input.value1")
      .sink("out", "monitor")
    val data = List(
      SimpleRecord("1", 0, "a", new Date(0))
    )

    val config = new Configuration()
    config.setString(ConfigConstants.METRICS_REPORTERS_LIST, "test")
    config.setString(ConfigConstants.METRICS_REPORTER_PREFIX + "test.class", classOf[TestReporter].getName)


    val env = new StreamExecutionEnvironment(JavaEnv.createLocalEnvironment(1, config))

    processInvoker.invoke(process, data, env)

    eventually {
      val totalGauges = TestReporter.taskManagerReporter.testGauges("error.instantRate")
      totalGauges.exists(_.getValue.asInstanceOf[Double] > 0) shouldBe true

      val nodeGauges = TestReporter.taskManagerReporter.testGauges("error.proc2.instantRateByNode")
      nodeGauges.exists(_.getValue.asInstanceOf[Double] > 0) shouldBe true
    }

  }

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

  override def notifyOfRemovedMetric(metric: Metric, metricName: String, group: MetricGroup) = {}

  override def close() = {}

  override def open(config: MetricConfig) = {
    TestReporter.instances.append(this)
  }

  override def filterCharacters(input: String) = input
}

