package pl.touk.nussknacker.engine.standalone

import com.codahale.metrics.MetricRegistry
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer
import pl.touk.nussknacker.engine.testing.LocalModelData

class StandaloneProcessInterpreterSpec extends FlatSpec with Matchers with Eventually with ScalaFutures {

  override implicit val patienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(100, Millis)
  )

  import spel.Implicits._

  import scala.concurrent.ExecutionContext.Implicits.global


  it should "run process in request response mode" in {

    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .filter("filter1", "#input.field1 == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .sink("endNodeIID", "#var1", "response-sink")

    val creator = new StandaloneProcessConfigCreator
    val result = runProcess(process, Request1("a", "b"), creator)

    result shouldBe Right(List(Response("alamakota")))
    creator.processorService.invocationsCount.get() shouldBe 1
  }

  it should "collect results after split" in {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
        .split("split",
          GraphBuilder.sink("sink1", "#input.field1", "response-sink"),
          GraphBuilder.sink("sink2", "#input.field2", "response-sink")
        )

    val result = runProcess(process, Request1("a", "b"))

    result shouldBe Right(List("a", "b"))
  }

  it should "collect metrics" in {

    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .filter("filter1", "#input.field1 == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .sink("endNodeIID", "#var1", "response-sink")

    val creator = new StandaloneProcessConfigCreator
    val metricRegistry = new MetricRegistry

    val interpreter = prepareInterpreter(process, creator, metricRegistry)
    interpreter.open()
    val result = interpreter.invoke(Request1("a", "b")).futureValue

    result shouldBe Right(List(Response("alamakota")))
    creator.processorService.invocationsCount.get() shouldBe 1

    eventually {
      metricRegistry.getGauges().get("proc1.instant.success").getValue.asInstanceOf[Double] should not be 0
      metricRegistry.getHistograms().get("proc1.times.success").getCount shouldBe 1
    }

    interpreter.close()
  }

  it should "collect results after element split" in {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .customNode("split", "outPart", "splitter", "parts" -> "#input.toList()")
      .sink("sink1", "#outPart", "response-sink")

    val result = runProcess(process, Request1("a", "b"))

    result shouldBe Right(List("a", "b"))
  }

  it should "init call open method for service" in {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .enricher("enricherWithOpenService", "response", "enricherWithOpenService")
      .sink("sink1", "#response.field1", "response-sink")

    val result = runProcess(process, Request1("a", "b"))

    result shouldBe Right(List("initialized!"))
  }

  it should "collect metrics for individual services" in {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .enricher("enricherWithOpenService", "response", "enricherWithOpenService")
      .sink("sink1", "#response.field1", "response-sink")

    val metricRegistry = new MetricRegistry

    val interpreter = prepareInterpreter(process, metricRegistry = metricRegistry)
    interpreter.open()
    val result = interpreter.invoke(Request1("a", "b")).futureValue

    result shouldBe Right(List("initialized!"))

    eventually {
      metricRegistry.getGauges().get("proc1.instant.success").getValue.asInstanceOf[Double] should not be 0
      metricRegistry.getHistograms().get("proc1.times.success").getCount shouldBe 1
      metricRegistry.getGauges().get("proc1.instant.enricherWithOpenService.OK").getValue.asInstanceOf[Double] should not be 0
      metricRegistry.getHistograms().get("proc1.times.enricherWithOpenService.OK").getCount shouldBe 1
    }
    interpreter.close()
  }


  def runProcess(process: EspProcess,
                 input: Any,
                 creator: StandaloneProcessConfigCreator = new StandaloneProcessConfigCreator,
                 metricRegistry: MetricRegistry = new MetricRegistry) = {
    val interpreter = prepareInterpreter(
      process = process,
      creator = creator,
      metricRegistry = metricRegistry
    )
    interpreter.open()
    val result = interpreter.invoke(input).futureValue
    interpreter.close()
    result
  }

  def prepareInterpreter(process: EspProcess,
                         creator: StandaloneProcessConfigCreator = new StandaloneProcessConfigCreator,
                         metricRegistry: MetricRegistry = new MetricRegistry) = {
    val simpleModelData = LocalModelData(ConfigFactory.load(), creator)
    val ctx = new StandaloneContextPreparer(metricRegistry)

    val maybeinterpreter = StandaloneProcessInterpreter(process, ctx, simpleModelData)

    maybeinterpreter shouldBe 'valid
    val interpreter = maybeinterpreter.toOption.get
    interpreter
  }

}