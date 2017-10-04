package pl.touk.nussknacker.engine.standalone

import java.util.concurrent.TimeUnit

import com.codahale.metrics.MetricRegistry
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class StandaloneProcessInterpreterSpec extends FlatSpec with Matchers with Eventually  {

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
      .source("start", "request1-source")
      .filter("filter1", "#input.field1 == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .sink("endNodeIID", "#var1", "response-sink")

    val input = Request1("a", "b")
    val config = ConfigFactory.load()
    val creator = new StandaloneProcessConfigCreator
    val ctx = new StandaloneContextPreparer(new MetricRegistry)
    val maybeinterpreter = StandaloneProcessInterpreter(process, ctx, creator, config)


    maybeinterpreter shouldBe 'valid
    val interpreter = maybeinterpreter.toOption.get
    interpreter.open()

    val result = interpreter.invoke(input)

    Await.result(result, Duration(5, TimeUnit.SECONDS)) shouldBe Right(List(Response("alamakota")))
    creator.processorService.invocationsCount.get() shouldBe 1

    interpreter.close()
  }

  it should "collect results after split" in {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-source")
        .split("split",
          GraphBuilder.sink("sink1", "#input.field1", "response-sink"),
          GraphBuilder.sink("sink2", "#input.field2", "response-sink")
        )

    val input = Request1("a", "b")
    val config = ConfigFactory.load()
    val creator = new StandaloneProcessConfigCreator
    val ctx = new StandaloneContextPreparer(new MetricRegistry)

    val maybeinterpreter = StandaloneProcessInterpreter(process, ctx, creator, config)

    maybeinterpreter shouldBe 'valid
    val interpreter = maybeinterpreter.toOption.get
    interpreter.open()

    val result = interpreter.invoke(input)

    Await.result(result, Duration(5, TimeUnit.SECONDS)) shouldBe Right(List("a", "b"))
    interpreter.close()
  }

  it should "collect metrics" in {

    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-source")
      .filter("filter1", "#input.field1 == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .sink("endNodeIID", "#var1", "response-sink")

    val input = Request1("a", "b")
    val config = ConfigFactory.load()
    val creator = new StandaloneProcessConfigCreator
    val metricRegistry = new MetricRegistry
    val ctx = new StandaloneContextPreparer(metricRegistry)
    val maybeinterpreter = StandaloneProcessInterpreter(process, ctx, creator, config)


    maybeinterpreter shouldBe 'valid
    val interpreter = maybeinterpreter.toOption.get
    interpreter.open()

    val result = interpreter.invoke(input)

    Await.result(result, Duration(5, TimeUnit.SECONDS)) shouldBe Right(List(Response("alamakota")))
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
      .source("start", "request1-source")
      .customNode("split", "outPart", "splitter", "parts" -> "#input.toList()")
      .sink("sink1", "#outPart", "response-sink")

    val input = Request1("a", "b")
    val config = ConfigFactory.load()
    val creator = new StandaloneProcessConfigCreator
    val ctx = new StandaloneContextPreparer(new MetricRegistry)

    val maybeinterpreter = StandaloneProcessInterpreter(process, ctx, creator, config)

    maybeinterpreter shouldBe 'valid
    val interpreter = maybeinterpreter.toOption.get
    interpreter.open()

    val result = interpreter.invoke(input)

    Await.result(result, Duration(5, TimeUnit.SECONDS)) shouldBe Right(List("a", "b"))
    interpreter.close()
  }

}