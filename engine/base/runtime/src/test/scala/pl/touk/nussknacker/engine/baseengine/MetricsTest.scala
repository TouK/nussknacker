package pl.touk.nussknacker.engine.baseengine

import io.dropwizard.metrics5.MetricRegistry
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.baseengine.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.EngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.baseengine.metrics.dropwizard.DropwizardMetricsProviderFactory
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.spel.Implicits._

import scala.jdk.CollectionConverters.mapAsScalaMapConverter

class MetricsTest extends FunSuite with Matchers {

  test("should measure node counts and source") {
    val metricRegistry = new MetricRegistry
    sample.run(sampleScenarioWithState, ScenarioInputBatch(List(0, 1, 2, 3).zipWithIndex.map { case (value, idx) =>
      (SourceId("start"), Context(idx.toString, Map("input" -> value), None))
    }), Map.empty, new EngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry)))

    def counterForNode(counterName: String)(nodeId: String) = metricRegistry.getCounters((mn, _)
      => mn.getKey == counterName && mn.getTags.asScala.toMap == Map("processId" -> sampleScenarioWithState.id, "nodeId" -> nodeId)).asScala.head._2.getCount
    val nodeCountForNode = counterForNode("nodeCount") _
    val errorCountForNode = counterForNode("error.instantRateByNode.count") _


    nodeCountForNode("start") shouldBe 4
    nodeCountForNode("failOnNumber1") shouldBe 4
    errorCountForNode("failOnNumber1") shouldBe 1
    nodeCountForNode("sum") shouldBe 3
    nodeCountForNode("end") shouldBe 3
  }

  private def sampleScenarioWithState: EspProcess = EspProcessBuilder
    .id("next")
    .exceptionHandler()
    .source("start", "start")
    .enricher("failOnNumber1", "out1", "failOnNumber1", "value" -> "#input")
    //we don't care about sum, only about node count
    .customNode("sum", "sum", "sum", "name" -> "''", "value" -> "0")
    .emptySink("end", "end", "value" -> "''")

}
