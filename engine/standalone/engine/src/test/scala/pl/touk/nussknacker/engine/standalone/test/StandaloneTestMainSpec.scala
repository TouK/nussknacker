package pl.touk.nussknacker.engine.standalone.test

import java.nio.charset.StandardCharsets

import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterEach, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.deployment.TestProcess._
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.standalone.{Request1, Response, StandaloneProcessConfigCreator}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.standalone.management.StandaloneTestMain
import pl.touk.nussknacker.engine.testing.LocalModelData

class StandaloneTestMainSpec extends FunSuite with Matchers with BeforeAndAfterEach {

  import spel.Implicits._

  private val modelData = LocalModelData(ConfigFactory.load(), new StandaloneProcessConfigCreator)

  private def marshall(process: EspProcess): String = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2
  
  test("perform test on mocks") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .filter("filter1", "#input.field1() == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .sink("endNodeIID", "#var1", "response-sink")

    val input = """{ "field1": "a", "field2": "b" }
      |{ "field1": "c", "field2": "d" }""".stripMargin
    val config = ConfigFactory.load()

    val results = StandaloneTestMain.run(
      processJson = marshall(process),
      modelData = modelData,
      testData = new TestData(input.getBytes(StandardCharsets.UTF_8)), variableEncoder = identity)

    results.nodeResults("filter1").toSet shouldBe Set(
      NodeResult(ResultContext("proc1-0", Map("input" -> Request1("a","b")))),
      NodeResult(ResultContext("proc1-1", Map("input" -> Request1("c","d"))))
    )

    results.invocationResults("filter1").toSet shouldBe Set(
      ExpressionInvocationResult(ResultContext("proc1-0", Map("input" -> Request1("a","b"))), "expression", true),
      ExpressionInvocationResult(ResultContext("proc1-1", Map("input" -> Request1("c","d"))), "expression", false)
    )

    results.mockedResults("processor").toSet shouldBe Set(MockedResult(ResultContext("proc1-0", Map()), "processorService", "processor service invoked"))
    results.mockedResults("endNodeIID").toSet shouldBe Set(MockedResult(ResultContext("proc1-0", Map("input" -> Request1("a","b"), "var1" -> Response("alamakota"))),
      "endNodeIID", """{
                      |  "field1" : "alamakota"
                      |}""".stripMargin))

    StandaloneProcessConfigCreator.processorService.get().invocationsCount.get shouldBe 0

  }

  test("detect errors in nodes") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .filter("occasionallyThrowFilter", "#input.field1() == 'a' ? 1/0 == 0 : true")
      .filter("filter1", "#input.field1() == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .sink("endNodeIID", "#var1", "response-sink")

    val input = """{ "field1": "a", "field2": "b" }
                  |{ "field1": "c", "field2": "d" }""".stripMargin
    val config = ConfigFactory.load()

    val results = StandaloneTestMain.run(
      processJson = marshall(process),
      modelData = modelData,
      testData = new TestData(input.getBytes(StandardCharsets.UTF_8)), variableEncoder = identity)

    results.invocationResults("occasionallyThrowFilter").toSet shouldBe Set(ExpressionInvocationResult(ResultContext("proc1-1", Map("input" -> Request1("c","d"))), "expression", true))
    results.exceptions should have size 1
    results.exceptions.head.context shouldBe ResultContext("proc1-0", Map("input" -> Request1("a","b")))
    results.exceptions.head.nodeId shouldBe Some("occasionallyThrowFilter")
    results.exceptions.head.throwable.getMessage shouldBe "/ by zero"
  }


  test("get results on parameter sinks") {
    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1()")

    val input = """{ "field1": "a", "field2": "b" }"""

    val results = StandaloneTestMain.run(
      processJson = marshall(process),
      modelData = modelData,
      testData = new TestData(input.getBytes(StandardCharsets.UTF_8)), variableEncoder = identity)

    results.nodeResults("endNodeIID").toSet shouldBe Set(
      NodeResult(ResultContext("proc1-0", Map("input" -> Request1("a","b"))))
    )

    results.mockedResults("endNodeIID").toSet shouldBe Set(
      MockedResult(ResultContext("proc1-0", Map("input" -> Request1("a","b"))), "endNodeIID", "a withRandomString")
    )

  }

}
