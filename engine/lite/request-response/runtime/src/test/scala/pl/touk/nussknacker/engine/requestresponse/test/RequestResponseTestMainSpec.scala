package pl.touk.nussknacker.engine.requestresponse.test

import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.runtimecontext.IncContextIdGenerator
import pl.touk.nussknacker.engine.api.test.{SingleSourceScenarioTestData, TestData}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.requestresponse.{FutureBasedRequestResponseScenarioInterpreter, Request1, RequestResponseConfigCreator, Response}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestProcess._

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext.Implicits.global

class RequestResponseTestMainSpec extends AnyFunSuite with Matchers with BeforeAndAfterEach {

  import pl.touk.nussknacker.engine.spel.Implicits._

  private val modelData = LocalModelData(ConfigFactory.load(), new RequestResponseConfigCreator)

  test("perform test on mocks") {
    val process = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .filter("filter1", "#input.field1() == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .processor("eagerProcessor", "collectingEager", "static" -> "'s'", "dynamic" -> "#input.field1()")
      .emptySink("endNodeIID", "response-sink", "value" -> "#var1")

    val input = """{ "field1": "a", "field2": "b" }
      |{ "field1": "c", "field2": "d" }""".stripMargin

    val results = FutureBasedRequestResponseScenarioInterpreter.testRunner.runTest(
      process = process,
      modelData = modelData,
      testData = SingleSourceScenarioTestData(TestData(input.getBytes(StandardCharsets.UTF_8)), 10), variableEncoder = identity)

    val contextIds = firstIdForFirstSource(process)
    val firstId = contextIds.nextContextId()
    val secondId = contextIds.nextContextId()
    
    results.nodeResults("filter1").toSet shouldBe Set(
      NodeResult(ResultContext(firstId, Map("input" -> Request1("a","b")))),
      NodeResult(ResultContext(secondId, Map("input" -> Request1("c","d"))))
    )

    results.invocationResults("filter1").toSet shouldBe Set(
      ExpressionInvocationResult(firstId, "expression", true),
      ExpressionInvocationResult(secondId, "expression", false)
    )

    results.externalInvocationResults("processor").toSet shouldBe Set(ExternalInvocationResult(firstId, "processorService", "processor service invoked"))
    results.externalInvocationResults("eagerProcessor").toSet shouldBe Set(ExternalInvocationResult(firstId, "collectingEager", "static-s-dynamic-a"))

    results.externalInvocationResults("endNodeIID").toSet shouldBe Set(ExternalInvocationResult(firstId, "endNodeIID", Response(s"alamakota-$firstId")))

    RequestResponseConfigCreator.processorService.get().invocationsCount.get shouldBe 0

  }

  test("detect errors in nodes") {
    val process = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .filter("occasionallyThrowFilter", "#input.field1() == 'a' ? 1/{0, 1}[0] == 0 : true")
      .filter("filter1", "#input.field1() == 'a'")
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .emptySink("endNodeIID", "response-sink", "value" -> "#var1")

    val input = """{ "field1": "a", "field2": "b" }
                  |{ "field1": "c", "field2": "d" }""".stripMargin

    val contextIds = firstIdForFirstSource(process)
    val firstId = contextIds.nextContextId()
    val secondId = contextIds.nextContextId()

    val results = FutureBasedRequestResponseScenarioInterpreter.testRunner.runTest(
      process = process,
      modelData = modelData,
      testData = SingleSourceScenarioTestData(TestData(input.getBytes(StandardCharsets.UTF_8)), 10), variableEncoder = identity)

    results.invocationResults("occasionallyThrowFilter").toSet shouldBe Set(ExpressionInvocationResult(secondId, "expression", true))
    results.exceptions should have size 1
    results.exceptions.head.context shouldBe ResultContext(firstId, Map("input" -> Request1("a","b")))
    results.exceptions.head.nodeId shouldBe Some("occasionallyThrowFilter")
    results.exceptions.head.throwable.getMessage shouldBe """Expression [#input.field1() == 'a' ? 1/{0, 1}[0] == 0 : true] evaluation failed, message: / by zero"""
  }


  test("get results on parameter sinks") {
    val process = ScenarioBuilder
      .requestResponse("proc1")
      .source("start", "request1-post-source")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1()")

    val input = """{ "field1": "a", "field2": "b" }"""

    val contextIds = firstIdForFirstSource(process)
    val firstId = contextIds.nextContextId()
    
    val results = FutureBasedRequestResponseScenarioInterpreter.testRunner.runTest(
      process = process,
      modelData = modelData,
      testData = SingleSourceScenarioTestData(TestData(input.getBytes(StandardCharsets.UTF_8)), 10), variableEncoder = identity)

    results.nodeResults("endNodeIID").toSet shouldBe Set(
      NodeResult(ResultContext(firstId, Map("input" -> Request1("a","b"))))
    )

    results.externalInvocationResults("endNodeIID").toSet shouldBe Set(
      ExternalInvocationResult(firstId, "endNodeIID", "a withRandomString")
    )

  }

  private def firstIdForFirstSource(scenario: CanonicalProcess): IncContextIdGenerator =
     IncContextIdGenerator.withProcessIdNodeIdPrefix(scenario.metaData, scenario.nodes.head.id)

}
