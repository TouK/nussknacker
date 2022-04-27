package pl.touk.nussknacker.engine.process.functional

import java.util.Date
import org.apache.flink.configuration.Configuration
import org.scalatest.{BeforeAndAfterEach, FunSuite, Matchers}
import org.scalatest.LoneElement._
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.graph.node.Case
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{MockService, SimpleRecord, SinkForStrings}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class MetricsSpec extends FunSuite with Matchers with VeryPatientScalaFutures with ProcessTestHelpers with BeforeAndAfterEach {

  import spel.Implicits.asSpelExpression

  override protected def beforeEach(): Unit = {
    TestReporter.reset(this.getClass.getName)
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestReporter.remove(this.getClass.getName)
  }

  test("measure time for service") {

    val process = ScenarioBuilder.streaming("proc1")
      .source("id", "input")
      .processor("proc2", "logService", "all" -> "#input.value2")
      .emptySink("out", "monitor")
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldNot be('empty)
    val histogram = TestReporter.get(this.getClass.getName).testHistogram("service.OK.serviceName.mockService.histogram")
    histogram.getCount shouldBe 1

  }

  test("measure errors") {

    val process = ScenarioBuilder.streaming("proc1")
      .source("id", "input")
      .processor("proc2", "logService", "all" -> "1 / #input.value1")
      .emptySink("out", "monitor")
    val data = List(
      SimpleRecord("1", 0, "a", new Date(0))
    )
    processInvoker.invokeWithSampleData(process, data)

    eventually {
      val reporter = TestReporter.get(this.getClass.getName)

      val totalGauges = reporter.testGauges("error.instantRate.instantRate")
      totalGauges.exists(_.getValue.asInstanceOf[Double] > 0) shouldBe true

      val nodeGauges = reporter.testGauges("error.instantRateByNode.nodeId.proc2.instantRate")
      nodeGauges.exists(_.getValue.asInstanceOf[Double] > 0) shouldBe true

      val nodeCounts = reporter.testCounters("error.instantRateByNode.nodeId.proc2")
      nodeCounts.exists(_.getCount > 0) shouldBe true
    }

  }

  test("measure node counts") {

    val process = ScenarioBuilder.streaming("proc1")
      .source("source1", "input")
      .filter("filter1", "#input.value1 == 10")
      .split("split1",
        GraphBuilder.emptySink("out2", "monitor"),
        GraphBuilder
          .processor("proc2", "logService", "all" -> "#input.value2")
          .emptySink("out", "monitor")
      )


    val data = List(
      SimpleRecord("1", 12, "a", new Date(0)),
      SimpleRecord("1", 10, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    eventually {
      counter("nodeId.source1.nodeCount") shouldBe 2L
      counter("nodeId.filter1.nodeCount") shouldBe 2L
      counter("nodeId.split1.nodeCount") shouldBe 1L
      counter("nodeId.proc2.nodeCount") shouldBe 1L
      counter("nodeId.out.nodeCount") shouldBe 1L
      counter("nodeId.out2.nodeCount") shouldBe 1L
    }
  }

  test("measure ends") {
    val data = List(
      SimpleRecord("1", 10, "a", new Date(0)),
      SimpleRecord("1", 12, "a", new Date(0)),
    )

    val process = ScenarioBuilder.streaming("proc1")
      .source("source", "input")
      .filter("filter", "#input.value1 > 10")
      .split("split",
        GraphBuilder.emptySink("sink", "monitor"),
        GraphBuilder.processorEnd("processor", "logService", "all" -> "#input.value2"),
        GraphBuilder.endingCustomNode("custom node", None, "optionalEndingCustom", "param" -> "#input.id")
      )

    processInvoker.invokeWithSampleData(process, data)

    eventually {
//      counter("end.nodeId.sink.count") shouldBe 1L
//      gauge("end.nodeId.sink.instantRate") should be >= 0.0d

      counter("end.nodeId.processor.count") shouldBe 1L
      gauge("end.nodeId.processor.instantRate") should be >= 0.0d

//      counter("end.nodeId.custom node.count") shouldBe 1L
//      gauge("end.nodeId.custom node.instantRate") should be >= 0.0d
    }
  }

  test("measure dead ends") {
    val data = List(
      SimpleRecord("1", 10, "a", new Date(0)),
      SimpleRecord("1", 11, "a", new Date(0)),
      SimpleRecord("1", 12, "a", new Date(0)),
      SimpleRecord("1", 13, "a", new Date(0)),
    )

    val process = ScenarioBuilder.streaming("proc1")
      .source("source", "input")
      .filter("filter1", "#input.value1 > 10")
      .switch("switch2", "#input.value1", "output",
        Case("#input.value1 > 12", GraphBuilder.emptySink("out", "monitor"))
      )

    processInvoker.invokeWithSampleData(process, data)

    eventually {
      counter("dead_end.nodeId.filter1.count") shouldBe 1L
      gauge("dead_end.nodeId.filter1.instantRate") should be >= 0.0d
      counter("dead_end.nodeId.switch2.count") shouldBe 2L
      gauge("dead_end.nodeId.switch2.instantRate") should be >= 0.0d
    }
  }

  test("open measuring service"){
    val process = ScenarioBuilder.streaming("proc1")
      .source("id", "input")
      .enricher("enricher1", "outputValue", "enricherWithOpenService")
      .emptySink("out", "sinkForStrings", "value" -> "#outputValue")

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)
    eventually {
      SinkForStrings.data shouldBe List("initialized!")
    }
  }

  private def counter(name: String): Long = {
    TestReporter.get(this.getClass.getName).testCounters(name).map(_.getCount).loneElement
  }

  private def gauge(name: String): Double = {
    TestReporter.get(this.getClass.getName).testGauges(name).map(_.getValue.asInstanceOf[Double]).loneElement
  }

  override protected def prepareFlinkConfiguration(): Configuration = {
    TestReporterUtil.configWithTestMetrics(new Configuration(), this.getClass.getName)
  }

}
