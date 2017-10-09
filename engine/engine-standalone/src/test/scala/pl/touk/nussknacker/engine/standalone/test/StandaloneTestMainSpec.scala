package pl.touk.nussknacker.engine.standalone.test

import java.nio.charset.StandardCharsets

import argonaut.PrettyParams
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.deployment.test._
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.standalone.management.StandaloneTestMain
import pl.touk.nussknacker.engine.standalone.{Request1, Response, StandaloneProcessConfigCreator}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.spel

class StandaloneTestMainSpec extends FlatSpec with Matchers with BeforeAndAfterEach {

  import spel.Implicits._

  val ProcessMarshaller = new ProcessMarshaller

  val modelData = LocalModelData(ConfigFactory.load(), new StandaloneProcessConfigCreator)

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

    val results = StandaloneTestMain.run(
      processJson = ProcessMarshaller.toJson(process, PrettyParams.spaces2),
      modelData = modelData,
      testData = new TestData(input.getBytes(StandardCharsets.UTF_8)))

    results.nodeResults("filter1").toSet shouldBe Set(
      NodeResult(Context("proc1-0", Map("input" -> Request1("a","b")))),
      NodeResult(Context("proc1-1", Map("input" -> Request1("c","d"))))
    )

    results.invocationResults("filter1").toSet shouldBe Set(
      ExpressionInvocationResult(Context("proc1-0", Map("input" -> Request1("a","b"))), "expression", true),
      ExpressionInvocationResult(Context("proc1-1", Map("input" -> Request1("c","d"))), "expression", false)
    )

    results.mockedResults("processor").toSet shouldBe Set(MockedResult(Context("proc1-0"), "processorService", "processor service invoked"))
    results.mockedResults("endNodeIID").toSet shouldBe Set(MockedResult(Context("proc1-0", Map("input" -> Request1("a","b"), "var1" -> Response("alamakota"))),
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

    val results = StandaloneTestMain.run(
      processJson = ProcessMarshaller.toJson(process, PrettyParams.spaces2),
      modelData = modelData,
      testData = new TestData(input.getBytes(StandardCharsets.UTF_8)))

    results.invocationResults("occasionallyThrowFilter").toSet shouldBe Set(ExpressionInvocationResult(Context("proc1-1", Map("input" -> Request1("c","d"))), "expression", true))
    results.exceptions should have size 1
    results.exceptions.head.context shouldBe Context("proc1-0", Map("input" -> Request1("a","b")))
    results.exceptions.head.nodeId shouldBe Some("occasionallyThrowFilter")
    results.exceptions.head.throwable.getMessage shouldBe "/ by zero"
  }

}
