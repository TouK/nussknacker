package pl.touk.nussknacker.engine.standalone

import com.codahale.metrics.MetricRegistry
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer
import pl.touk.nussknacker.engine.testing.LocalModelData

class StandaloneProcessInterpreterSpec extends FunSuite with Matchers with Eventually with ScalaFutures {

  override implicit val patienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(100, Millis)
  )

  import spel.Implicits._

  import scala.concurrent.ExecutionContext.Implicits.global


  test("run process in request response mode") {

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

  test("collects answers from parameters") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1 + 'd'")

    val creator = new StandaloneProcessConfigCreator
    val result = runProcess(process, Request1("abc", "b"), creator)

    result shouldBe Right(List("abcd withRandomString"))
  }

  test("collect results after split") {
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

  test("collect metrics") {

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
    interpreter.open(JobData(process.metaData, ProcessVersion.empty))
    val result = interpreter.invoke(Request1("a", "b")).futureValue

    result shouldBe Right(List(Response("alamakota")))
    creator.processorService.invocationsCount.get() shouldBe 1

    eventually {
      metricRegistry.getGauges().get("proc1.serviceInstant.success").getValue.asInstanceOf[Double] should not be 0
      metricRegistry.getHistograms().get("proc1.serviceTimes.success").getCount shouldBe 1
    }

    interpreter.close()
  }

  test("collect results after element split") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .customNode("split", "outPart", "splitter", "parts" -> "#input.toList()")
      .buildSimpleVariable("var1", "var1", "#outPart")
      .sink("sink1", "#outPart", "response-sink")

    val result = runProcess(process, Request1("a", "b"))

    result shouldBe Right(List("a", "b"))
  }

  test("init call open method for service") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .enricher("enricherWithOpenService", "response", "enricherWithOpenService")
      .sink("sink1", "#response.field1", "response-sink")

    val result = runProcess(process, Request1("a", "b"))

    result shouldBe Right(List("initialized!"))
  }

  test("collect metrics for individual services") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .enricher("enricherWithOpenService", "response", "enricherWithOpenService")
      .sink("sink1", "#response.field1", "response-sink")

    val metricRegistry = new MetricRegistry

    val interpreter = prepareInterpreter(process, metricRegistry = metricRegistry)
    interpreter.open(JobData(process.metaData, ProcessVersion.empty))
    val result = interpreter.invoke(Request1("a", "b")).futureValue

    result shouldBe Right(List("initialized!"))

    eventually {
      metricRegistry.getGauges().get("proc1.serviceInstant.success").getValue.asInstanceOf[Double] should not be 0
      metricRegistry.getHistograms().get("proc1.serviceTimes.success").getCount shouldBe 1
      metricRegistry.getGauges().get("proc1.serviceInstant.enricherWithOpenService.OK").getValue.asInstanceOf[Double] should not be 0
      metricRegistry.getHistograms().get("proc1.serviceTimes.enricherWithOpenService.OK").getCount shouldBe 1
    }
    interpreter.close()
  }

  test("run process using custom node with ContextTransformation API") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .customNode("extract", "extracted", "extractor", "expression" -> "#input.field2")
      .sink("sink1", "#extracted", "response-sink")

    val result = runProcess(process, Request1("a", "b"))

    result shouldBe Right(List("b"))
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
    interpreter.open(JobData(process.metaData,ProcessVersion.empty))
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