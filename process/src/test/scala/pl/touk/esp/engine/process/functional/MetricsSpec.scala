package pl.touk.esp.engine.process.functional

import java.util.Date

import org.apache.flink.configuration.{ConfigConstants, Configuration}
import org.apache.flink.metrics.reporter.AbstractReporter
import org.apache.flink.metrics.{Metric, MetricConfig, MetricGroup}
import org.apache.flink.streaming.api.environment.{StreamExecutionEnvironment => JavaEnv}
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.process.ProcessTestHelpers.{MockService, SimpleRecord, processInvoker}
import pl.touk.esp.engine.spel

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

class MetricsSpec extends FlatSpec with Matchers with Eventually with BeforeAndAfterEach {

  val config = new Configuration()
  config.setString(ConfigConstants.METRICS_REPORTERS_LIST, "test")
  config.setString(ConfigConstants.METRICS_REPORTER_PREFIX + "test.class", classOf[TestReporter].getName)

  override protected def beforeEach(): Unit = {
    TestReporter.reset()
  }

  override implicit val patienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(100, Millis)
  )

  it should "measure time for service" in {

    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .processor("proc2", "logService", "all" -> "#input.value2")
      .sink("out", "monitor")
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    invoke(process, data)

    MockService.data shouldNot be('empty)
    val histogram = TestReporter.taskManagerReporter.testHistogram("serviceTimes.mockService.OK")
    histogram.getCount shouldBe 1

  }

  it should "measure errors" in {

    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .processor("proc2", "logService", "all" -> "1 / #input.value1")
      .sink("out", "monitor")
    val data = List(
      SimpleRecord("1", 0, "a", new Date(0))
    )
    invoke(process, data)

    eventually {
      val totalGauges = TestReporter.taskManagerReporter.testGauges("error.instantRate")
      totalGauges.exists(_.getValue.asInstanceOf[Double] > 0) shouldBe true

      val nodeGauges = TestReporter.taskManagerReporter.testGauges("error.proc2.instantRateByNode")
      nodeGauges.exists(_.getValue.asInstanceOf[Double] > 0) shouldBe true
    }

  }

  it should "measure node counts" in {

    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("source1", "input")
      .filter("filter1", "#input.value1 == 10")
      .processor("proc2", "logService", "all" -> "#input.value2")
      .sink("out", "monitor")
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0)),
      SimpleRecord("1", 10, "a", new Date(0))
    )

    invoke(process, data)

    def counter(name: String) =
      TestReporter.taskManagerReporter.testCounters(name).map(_.getCount).find(_ > 0).getOrElse(0)

    eventually {
      counter("nodeCount.source1") shouldBe 2L
      counter("nodeCount.filter1") shouldBe 2L
      counter("nodeCount.proc2") shouldBe 1L
      counter("nodeCount.out") shouldBe 1L
    }
  }

  private def invoke(process: EspProcess, data: List[SimpleRecord]) = {
    val env = new StreamExecutionEnvironment(JavaEnv.createLocalEnvironment(1, config))
    processInvoker.invoke(process, data, env)
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

  def testCounters(containing: String) = counters.toMap.filter(_._2.contains(containing)).keys

  override def notifyOfRemovedMetric(metric: Metric, metricName: String, group: MetricGroup) = {}

  override def close() = {}

  override def open(config: MetricConfig) = {
    TestReporter.instances.append(this)
  }

  override def filterCharacters(input: String) = input
}

