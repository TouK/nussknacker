package pl.touk.nussknacker.engine.process.functional

import cats.data.NonEmptyList

import java.util.Date
import org.apache.flink.configuration.Configuration
import org.scalatest.{BeforeAndAfterEach, FunSuite, Matchers}
import org.scalatest.LoneElement._
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.{Case, DeadEndingData, EndingNodeData}
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{MockService, SimpleRecord, SinkForStrings}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.split.NodesCollector
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

    counter("nodeId.source1.nodeCount") shouldBe 2L
    counter("nodeId.filter1.nodeCount") shouldBe 2L
    counter("nodeId.split1.nodeCount") shouldBe 1L
    counter("nodeId.proc2.nodeCount") shouldBe 1L
    counter("nodeId.out.nodeCount") shouldBe 1L
    counter("nodeId.out2.nodeCount") shouldBe 1L
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

    counter("end.nodeId.sink.count") shouldBe 1L
    gauge("end.nodeId.sink.instantRate") should be >= 0.0d

    counter("end.nodeId.processor.count") shouldBe 1L
    gauge("end.nodeId.processor.instantRate") should be >= 0.0d

    counter("end.nodeId.custom node.count") shouldBe 1L
    gauge("end.nodeId.custom node.instantRate") should be >= 0.0d
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

    counter("dead_end.nodeId.filter1.count") shouldBe 1L
    gauge("dead_end.nodeId.filter1.instantRate") should be >= 0.0d
    counter("dead_end.nodeId.switch2.count") shouldBe 2L
    gauge("dead_end.nodeId.switch2.instantRate") should be >= 0.0d
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
    SinkForStrings.data shouldBe List("initialized!")
  }

  test("initializes counts, ends, dead ends") {

    val scenario = EspProcess(MetaData("proc1", StreamMetaData()), NonEmptyList.of(
      GraphBuilder.source("id", "input")
        .split("split",
          GraphBuilder.filter("left", "false").branchEnd("end1", "join1"),
          GraphBuilder.filter("right", "false").branchEnd("end2", "join1")
        ),
      GraphBuilder.join("join1", "joinBranchExpression", Some("any"),
        List(
          "end1" -> List("value" -> "''"),
          "end2" -> List("value" -> "''")
        ))
        .customNodeNoOutput("custom", "customFilter", "input" -> "''", "stringVal" -> "''")
        .processor("proc1", "lifecycleService")
        .switch("switch1", "false", "any2",
          GraphBuilder.emptySink("outE1", "sinkForStrings", "value" -> "''"),
          Case("true", GraphBuilder.processorEnd("procE1", "lifecycleService")),
          Case("false", GraphBuilder.endingCustomNode("customE1", None,"optionalEndingCustom", "param" -> "''"))
        )
    ))
    val allNodes = NodesCollector.collectNodesInScenario(scenario).map(_.data)

    processInvoker.invokeWithSampleData(scenario, Nil)

    allNodes.foreach { node =>
      counter(s"nodeId.${node.id}.nodeCount") shouldBe 0L
    }
    allNodes.filter(_.isInstanceOf[EndingNodeData]).foreach { node =>
      counter(s"end.nodeId.${node.id}.count") shouldBe 0L
    }
    allNodes.filter(_.isInstanceOf[DeadEndingData]).foreach { node =>
      counter(s"dead_end.nodeId.${node.id}.count") shouldBe 0L
    }
  }

  private def counter(name: String): Long = withClue(s"counter $name") {
    TestReporter.get(this.getClass.getName).testCounters(name).loneElement.getCount
  }

  private def gauge(name: String): Double = withClue(s"gauge $name"){
    TestReporter.get(this.getClass.getName).testGauges(name).loneElement.getValue.asInstanceOf[Double]
  }

  override protected def prepareFlinkConfiguration(): Configuration = {
    TestReporterUtil.configWithTestMetrics(new Configuration(), this.getClass.getName)
  }

}
