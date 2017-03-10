package pl.touk.esp.engine.standalone

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.spel

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class StandaloneProcessInterpreterSpec extends FlatSpec with Matchers {

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
    val maybeinterpreter = StandaloneProcessInterpreter(process, creator, config)


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
    val maybeinterpreter = StandaloneProcessInterpreter(process, creator, config)

    maybeinterpreter shouldBe 'valid
    val interpreter = maybeinterpreter.toOption.get
    interpreter.open()

    val result = interpreter.invoke(input)

    Await.result(result, Duration(5, TimeUnit.SECONDS)) shouldBe Right(List("a", "b"))
    interpreter.close()
  }

}