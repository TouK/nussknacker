package pl.touk.nussknacker.engine.requestresponse.test

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterEach, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.deployment.TestProcess._
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.requestresponse.{Request1, Response, StandaloneProcessConfigCreator, RequestResponseEngine}
import pl.touk.nussknacker.engine.testing.LocalModelData

import java.nio.charset.StandardCharsets

class StandaloneTestMainSpec extends FunSuite with Matchers with BeforeAndAfterEach {

  import spel.Implicits._

  private val modelData = LocalModelData(ConfigFactory.load(), new StandaloneProcessConfigCreator)

  test("perform test on mocks") {
    val process = EspProcessBuilder
      .id("proc1")
      .source("start", "request1-post-source")
      .filter("filter1", "#input.field1() == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .processor("eagerProcessor", "collectingEager", "static" -> "'s'", "dynamic" -> "#input.field1()")
      .emptySink("endNodeIID", "response-sink", "value" -> "#var1")

    val input = """{ "field1": "a", "field2": "b" }
      |{ "field1": "c", "field2": "d" }""".stripMargin

    val results = RequestResponseEngine.testRunner.runTest(
      process = process,
      modelData = modelData,
      testData = new TestData(input.getBytes(StandardCharsets.UTF_8), 10), variableEncoder = identity)

    results.nodeResults("filter1").toSet shouldBe Set(
      NodeResult(ResultContext("test-0", Map("input" -> Request1("a","b")))),
      NodeResult(ResultContext("test-1", Map("input" -> Request1("c","d"))))
    )

    results.invocationResults("filter1").toSet shouldBe Set(
      ExpressionInvocationResult("test-0", "expression", true),
      ExpressionInvocationResult("test-1", "expression", false)
    )

    results.mockedResults("processor").toSet shouldBe Set(MockedResult("test-0", "processorService", "processor service invoked"))
    results.mockedResults("eagerProcessor").toSet shouldBe Set(MockedResult("test-0", "collectingEager", "static-s-dynamic-a"))

    results.mockedResults("endNodeIID").toSet shouldBe Set(MockedResult("test-0", "endNodeIID", Response("alamakota-test-0")))

    StandaloneProcessConfigCreator.processorService.get().invocationsCount.get shouldBe 0

  }

  test("detect errors in nodes") {
    val process = EspProcessBuilder
      .id("proc1")
      .source("start", "request1-post-source")
      .filter("occasionallyThrowFilter", "#input.field1() == 'a' ? 1/0 == 0 : true")
      .filter("filter1", "#input.field1() == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .emptySink("endNodeIID", "response-sink", "value" -> "#var1")

    val input = """{ "field1": "a", "field2": "b" }
                  |{ "field1": "c", "field2": "d" }""".stripMargin

    val results = RequestResponseEngine.testRunner.runTest(
      process = process,
      modelData = modelData,
      testData = new TestData(input.getBytes(StandardCharsets.UTF_8), 10), variableEncoder = identity)

    results.invocationResults("occasionallyThrowFilter").toSet shouldBe Set(ExpressionInvocationResult("test-1", "expression", true))
    results.exceptions should have size 1
    results.exceptions.head.context shouldBe ResultContext("test-0", Map("input" -> Request1("a","b")))
    results.exceptions.head.nodeId shouldBe Some("occasionallyThrowFilter")
    results.exceptions.head.throwable.getMessage shouldBe """Expression [#input.field1() == 'a' ? 1/0 == 0 : true] evaluation failed, message: / by zero"""
  }


  test("get results on parameter sinks") {
    val process = EspProcessBuilder
      .id("proc1")
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1()")

    val input = """{ "field1": "a", "field2": "b" }"""

    val results = RequestResponseEngine.testRunner.runTest(
      process = process,
      modelData = modelData,
      testData = new TestData(input.getBytes(StandardCharsets.UTF_8), 10), variableEncoder = identity)

    results.nodeResults("endNodeIID").toSet shouldBe Set(
      NodeResult(ResultContext("test-0", Map("input" -> Request1("a","b"))))
    )

    results.mockedResults("endNodeIID").toSet shouldBe Set(
      MockedResult("test-0", "endNodeIID", "a withRandomString")
    )

  }

}
