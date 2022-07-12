package pl.touk.nussknacker.engine.process.functional

import cats.data.NonEmptyList
import org.apache.flink.configuration.Configuration
import org.apache.flink.metrics.{Counter, Gauge, Histogram}
import org.scalatest.LoneElement.convertToCollectionLoneElementWrapper
import org.scalatest.{BeforeAndAfterEach, Matchers, Outcome, fixture}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.{Case, DeadEndingData, EndingNodeData}
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{MockService, SimpleRecord, SinkForStrings}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.split.NodesCollector
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import spel.Implicits.asSpelExpression
import java.util.Date

class MetricsSpec extends fixture.FunSuite with Matchers with VeryPatientScalaFutures with ProcessTestHelpers with BeforeAndAfterEach {

  private val reporterName = getClass.getName

  private def reporter: TestReporter = TestReporter.get(reporterName)

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestReporter.remove(reporterName)
  }

  type FixtureParam = ProcessName

  def withFixture(test: OneArgTest): Outcome = {
    //this *has* to be scenario name in the test
    withFixture(test.toNoArgTest(ProcessName(test.name)))
  }

  test("measure time for service") { implicit scenarioName =>

    val process = ScenarioBuilder.streaming(scenarioName.value)
      .source("id", "input")
      .processor("proc2", "logService", "all" -> "#input.value2")
      .emptySink("out", "monitor")
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldNot be('empty)
    val histogram = reporter.testMetrics[Histogram]("service.OK.serviceName.mockService.histogram").loneElement
    histogram.getCount shouldBe 1

  }

  test("measure errors") { implicit scenarioName =>

    val process = ScenarioBuilder.streaming(scenarioName.value)
      .source("id", "input")
      .processor("proc2", "logService", "all" -> "1 / #input.value1")
      .emptySink("out", "monitor")
    val data = List(
      SimpleRecord("1", 0, "a", new Date(0))
    )
    processInvoker.invokeWithSampleData(process, data)

    //we measure counts, as instant rate is reset after read so it's quite unstable...
    eventually {
      val totalCounter = reporter.testMetrics[Counter]("error.instantRate")
      totalCounter.exists(_.getCount > 0) shouldBe true

      val nodeCounts = reporter.testMetrics[Counter]("error.instantRateByNode.nodeId.proc2")
      nodeCounts.exists(_.getCount > 0) shouldBe true
    }

  }

  test("measure node counts") { implicit scenarioName =>

    val process = ScenarioBuilder.streaming(scenarioName.value)
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

  test("measure ends") { implicit scenarioName =>
    val data = List(
      SimpleRecord("1", 10, "a", new Date(0)),
      SimpleRecord("1", 12, "a", new Date(0)),
    )

    val process = ScenarioBuilder.streaming(scenarioName.value)
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

  test("measure dead ends") { implicit scenarioName =>
    val data = List(
      SimpleRecord("1", 10, "a", new Date(0)),
      SimpleRecord("1", 11, "a", new Date(0)),
      SimpleRecord("1", 12, "a", new Date(0)),
      SimpleRecord("1", 13, "a", new Date(0)),
    )

    val process = ScenarioBuilder.streaming(scenarioName.value)
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

  test("open measuring service"){ implicit scenarioName =>
    val process = ScenarioBuilder.streaming(scenarioName.value)
      .source("id", "input")
      .enricher("enricher1", "outputValue", "enricherWithOpenService")
      .emptySink("out", "sinkForStrings", "value" -> "#outputValue")

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)
    SinkForStrings.data shouldBe List("initialized!")
  }

  test("initializes counts, ends, dead ends") { implicit scenarioName =>

    val scenario = EspProcess(MetaData(scenarioName.value, StreamMetaData()), NonEmptyList.of(
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

  private def counter(name: String)(implicit scenarioName: ProcessName): Long = withClue(s"counter $name") {
    reporter.testMetrics[Counter](name).loneElement.getCount
  }

  private def gauge(name: String)(implicit scenarioName: ProcessName): Double = withClue(s"gauge $name"){
    reporter.testMetrics[Gauge[Double]](name).loneElement.getValue
  }

  override protected def prepareFlinkConfiguration(): Configuration = {
    TestReporterUtil.configWithTestMetrics(reporterName)
  }

}
