package pl.touk.nussknacker.engine.lite

import cats.data.NonEmptyList
import io.dropwizard.metrics5.{MetricFilter, MetricRegistry}
import org.scalatest.LoneElement._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.{Case, DeadEndingData, EndingNodeData}
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.DropwizardMetricsProviderFactory
import pl.touk.nussknacker.engine.lite.sample.SampleInput
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.metrics.common.naming.{nodeIdTag, scenarioIdTag}
import pl.touk.nussknacker.engine.util.metrics.{Gauge, MetricIdentifier}

import scala.jdk.CollectionConverters._

class MetricsTest extends AnyFunSuite with Matchers {

  private val scenarioId = "metrics"
  private val sourceId   = "start"

  test("should measure node counts and source") {
    val metricRegistry = new MetricRegistry
    val sampleScenarioWithState = ScenarioBuilder
      .streamingLite(scenarioId)
      .source(sourceId, "start")
      .enricher("failOnNumber1", "out1", "failOnNumber1", "value" -> "#input".spel)
      // we don't care about sum, only about node count
      .customNode("sum", "sum", "sum", "name" -> "''".spel, "value" -> "0".spel)
      .emptySink("end", "end", "value" -> "''".spel)

    runScenario(sampleScenarioWithState, List(0, 1, 2, 3), metricRegistry)

    metricRegistry.nodeCountForNode("start") shouldBe 4
    metricRegistry.nodeCountForNode("failOnNumber1") shouldBe 4
    metricRegistry.errorCountForNode("failOnNumber1") shouldBe 1
    metricRegistry.nodeCountForNode("sum") shouldBe 3
    metricRegistry.nodeCountForNode("end") shouldBe 3
  }

  test("should measure ends") {
    val metricRegistry = new MetricRegistry
    val scenario = ScenarioBuilder
      .streamingLite(scenarioId)
      .source("start", "start")
      .filter("filter", "#input > 0".spel)
      .split(
        "split",
        GraphBuilder.emptySink("sink", "end", "value"                   -> "''".spel),
        GraphBuilder.processorEnd("processor", "noOpProcessor", "value" -> "#input".spel)
      )

    runScenario(scenario, List(0, 1, 2, 3), metricRegistry)

    metricRegistry.endCountForNode("sink") shouldBe 3L
    metricRegistry.endInstantRateForNode("sink") should be >= 0.0d
    metricRegistry.endCountForNode("processor") shouldBe 3L
    metricRegistry.endInstantRateForNode("processor") should be >= 0.0d
  }

  test("should measure dead ends") {
    val metricRegistry = new MetricRegistry
    val scenario = ScenarioBuilder
      .streamingLite(scenarioId)
      .source("start", "start")
      .filter("filter1", "#input > 0".spel)
      .switch(
        "switch2",
        "#input".spel,
        "output",
        Case("#input > 2".spel, GraphBuilder.emptySink("end", "end", "value" -> "''".spel))
      )

    runScenario(scenario, List(0, 1, 2, 3), metricRegistry)

    metricRegistry.deadEndCountForNode("filter1") shouldBe 1L
    metricRegistry.deadEndInstantRateForNode("filter1") should be >= 0.0d
    metricRegistry.deadEndCountForNode("switch2") shouldBe 2L
    metricRegistry.deadEndInstantRateForNode("switch2") should be >= 0.0d
  }

  test("should unregister metrics") {
    val metricRegistry   = new MetricRegistry
    val metricProvider   = new DropwizardMetricsProviderFactory(metricRegistry)(ProcessName("fooScenarioId"))
    val metricIdentifier = MetricIdentifier(NonEmptyList.one("foo"), Map.empty)
    val someGauge = new Gauge[Int] {
      override def getValue: Int = 123
    }
    metricProvider.registerGauge(metricIdentifier, someGauge)
    a[IllegalArgumentException] should be thrownBy {
      metricProvider.registerGauge(metricIdentifier, someGauge)
    }
    metricProvider.remove(metricIdentifier)
    metricProvider.registerGauge(metricIdentifier, someGauge)
  }

  private def runScenario(scenario: CanonicalProcess, input: List[Int], metricRegistry: MetricRegistry): Unit = {
    sample.run(
      scenario,
      ScenarioInputBatch(input.zipWithIndex.map { case (value, idx) =>
        (SourceId(sourceId), SampleInput(idx.toString, value))
      }),
      Map.empty,
      new LiteEngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))
    )
  }

  test("initializes counts, ends, dead ends") {
    val metricRegistry = new MetricRegistry

    val scenario = ScenarioBuilder
      .streaming(scenarioId)
      .source("source1", "start")
      .filter("filter1", "false".spel)
      .processor("processor1", "noOpProcessor", "value" -> "0".spel)
      .emptySink("sink1", "end", "value" -> "''".spel)
    val allNodes = scenario.collectAllNodes

    runScenario(scenario, Nil, metricRegistry)

    allNodes.map(_.id).foreach(metricRegistry.nodeCountForNode)
    allNodes.filter(_.isInstanceOf[EndingNodeData]).map(_.id).foreach(metricRegistry.endCountForNode)
    allNodes.filter(_.isInstanceOf[DeadEndingData]).map(_.id).foreach(metricRegistry.deadEndCountForNode)
  }

  implicit class MetricsTestHelper(metricRegistry: MetricRegistry) {

    def counterForNode(counterName: String)(nodeId: String): Long =
      withClue(s"counter: $counterName, nodeId: $nodeId") {
        metricRegistry.getCounters(nodeFilter(counterName, nodeId)).asScala.loneElement._2.getCount
      }

    def gaugeForNode(gaugeName: String)(nodeId: String): Double = withClue(s"gauge: $gaugeName, nodeId: $nodeId") {
      metricRegistry.getGauges(nodeFilter(gaugeName, nodeId)).asScala.loneElement._2.getValue.asInstanceOf[Double]
    }

    val nodeCountForNode: String => Long            = counterForNode("nodeCount")
    val errorCountForNode: String => Long           = counterForNode("error.instantRateByNode.count")
    val endCountForNode: String => Long             = counterForNode("end.count")
    val endInstantRateForNode: String => Double     = gaugeForNode("end.instantRate")
    val deadEndCountForNode: String => Long         = counterForNode("dead_end.count")
    val deadEndInstantRateForNode: String => Double = gaugeForNode("dead_end.instantRate")

    private def nodeFilter(key: String, nodeId: String): MetricFilter = { (mn, _) =>
      mn.getKey == key && mn.getTags.asScala.toMap == Map(scenarioIdTag -> scenarioId, nodeIdTag -> nodeId)
    }

  }

}
