package pl.touk.nussknacker.engine.lite

import cats.data.NonEmptyList
import io.dropwizard.metrics5.MetricRegistry
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.DropwizardMetricsProviderFactory
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.metrics.{Gauge, MetricIdentifier}

import scala.jdk.CollectionConverters.mapAsScalaMapConverter

class MetricsTest extends FunSuite with Matchers {

  test("should measure node counts and source") {
    val metricRegistry = new MetricRegistry
    sample.run(sampleScenarioWithState, ScenarioInputBatch(List(0, 1, 2, 3).zipWithIndex.map { case (value, idx) =>
      (SourceId("start"), Context(idx.toString, Map("input" -> value), None))
    }), Map.empty, new LiteEngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry)))

    def counterForNode(counterName: String)(nodeId: String) = metricRegistry.getCounters((mn, _)
      => mn.getKey == counterName && mn.getTags.asScala.toMap == Map("process" -> sampleScenarioWithState.id, "nodeId" -> nodeId)).asScala.head._2.getCount
    val nodeCountForNode = counterForNode("nodeCount") _
    val errorCountForNode = counterForNode("error.instantRateByNode.count") _


    nodeCountForNode("start") shouldBe 4
    nodeCountForNode("failOnNumber1") shouldBe 4
    errorCountForNode("failOnNumber1") shouldBe 1
    nodeCountForNode("sum") shouldBe 3
    nodeCountForNode("end") shouldBe 3
  }

  test("should unregister metrics") {
    val metricRegistry = new MetricRegistry
    val metricProvider = new DropwizardMetricsProviderFactory(metricRegistry)("fooScenarioId")
    val metricIdentifier = MetricIdentifier(NonEmptyList("foo", Nil), Map.empty)
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

  private def sampleScenarioWithState: EspProcess = EspProcessBuilder
    .id("next")
    .source("start", "start")
    .enricher("failOnNumber1", "out1", "failOnNumber1", "value" -> "#input")
    //we don't care about sum, only about node count
    .customNode("sum", "sum", "sum", "name" -> "''", "value" -> "0")
    .emptySink("end", "end", "value" -> "''")

}
