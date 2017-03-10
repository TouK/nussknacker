package pl.touk.esp.engine.standalone.test

import argonaut.PrettyParams
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.esp.engine.api.Context
import pl.touk.esp.engine.api.deployment.test._
import pl.touk.esp.engine.api.exception.EspExceptionInfo
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.engine.spel
import pl.touk.esp.engine.standalone.management.StandaloneTestMain
import pl.touk.esp.engine.standalone.{ProcessorService, Request1, Response, StandaloneProcessConfigCreator}

class StandaloneTestMainSpec extends FlatSpec with Matchers with BeforeAndAfterEach {

  import spel.Implicits._
  import scala.concurrent.ExecutionContext.Implicits.global

  val ProcessMarshaller = new ProcessMarshaller

  it should "perform test on mocks" in {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-source")
      .filter("filter1", "#input.field1() == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .sink("endNodeIID", "#var1", "response-sink")

    val input = """{ "field1": "a", "field2": "b" }
      |{ "field1": "c", "field2": "d" }""".stripMargin
    val config = ConfigFactory.load()
      .withValue("processConfigCreatorClass", fromAnyRef("pl.touk.esp.engine.standalone.StandaloneProcessConfigCreator"))

    val results = StandaloneTestMain.run(ProcessMarshaller.toJson(process, PrettyParams.spaces2), config, new TestData(input.getBytes), List())

    results.nodeResults("filter1") shouldBe List(
      NodeResult(Context("proc1-0", Map("input" -> Request1("a","b")))),
      NodeResult(Context("proc1-1", Map("input" -> Request1("c","d"))))
    )

    results.invocationResults("filter1") shouldBe List(
      ExpressionInvocationResult(Context("proc1-0", Map("input" -> Request1("a","b"))), "expression", true),
      ExpressionInvocationResult(Context("proc1-1", Map("input" -> Request1("c","d"))), "expression", false)
    )

    results.mockedResults("processor") shouldBe List(MockedResult(Context("proc1-0"), "processorService", "processor service invoked"))
    results.mockedResults("endNodeIID") shouldBe List(MockedResult(Context("proc1-0", Map("input" -> Request1("a","b"), "var1" -> Response("alamakota"))),
      //FIXME: w tej chwili tu nie wpisuje refa, tylko nodeId... poza tym co to za wynik??
      "endNodeIID", "Response(alamakota)"))

    StandaloneProcessConfigCreator.processorService.get().invocationsCount.get shouldBe 0

  }

  it should "detect errors in nodes" in {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-source")
      .filter("occasionallyThrowFilter", "#input.field1() == 'a' ? 1/0 == 0 : true")
      .filter("filter1", "#input.field1() == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .sink("endNodeIID", "#var1", "response-sink")

    val input = """{ "field1": "a", "field2": "b" }
                  |{ "field1": "c", "field2": "d" }""".stripMargin
    val config = ConfigFactory.load()
      .withValue("processConfigCreatorClass", fromAnyRef("pl.touk.esp.engine.standalone.StandaloneProcessConfigCreator"))

    val results = StandaloneTestMain.run(ProcessMarshaller.toJson(process, PrettyParams.spaces2), config, new TestData(input.getBytes), List())

    results.invocationResults("occasionallyThrowFilter") shouldBe List(ExpressionInvocationResult(Context("proc1-1", Map("input" -> Request1("c","d"))), "expression", true))
    results.exceptions should have size 1
    results.exceptions.head.context shouldBe Context("proc1-0", Map("input" -> Request1("a","b")))
    results.exceptions.head.nodeId shouldBe Some("occasionallyThrowFilter")
    results.exceptions.head.throwable.getMessage shouldBe "/ by zero"
  }

}
