package pl.touk.nussknacker.engine.requestresponse.test

import com.typesafe.config.ConfigFactory
import io.circe.Json
import org.scalatest.BeforeAndAfterEach
import org.scalatest.LoneElement._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{DisplayJsonWithEncoder, JobData, ProcessVersion}
import pl.touk.nussknacker.engine.api.runtimecontext.IncContextIdGenerator
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.components.LiteBaseComponentProvider
import pl.touk.nussknacker.engine.lite.components.requestresponse.RequestResponseComponentProvider
import pl.touk.nussknacker.engine.requestresponse.{
  FutureBasedRequestResponseScenarioInterpreter,
  Request1,
  RequestResponseSampleComponents,
  Response
}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestProcess._

import scala.concurrent.ExecutionContext.Implicits.global

class RequestResponseTestMainSpec extends AnyFunSuite with Matchers with BeforeAndAfterEach {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  val requestResponseSampleComponents = new RequestResponseSampleComponents

  private val modelData = LocalModelData(
    ConfigFactory.load(),
    requestResponseSampleComponents.components :::
      LiteBaseComponentProvider.Components :::
      RequestResponseComponentProvider.Components
  )

  private val sourceId = "start"

  test("perform test on mocks") {
    val process = ScenarioBuilder
      .requestResponse("proc1")
      .source(sourceId, "request1-post-source")
      .filter("filter1", "#input.field1() == 'a'".spel)
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .processor("eagerProcessor", "collectingEager", "static" -> "'s'".spel, "dynamic" -> "#input.field1()".spel)
      .emptySink("endNodeIID", "response-sink", "value" -> "#var1".spel)

    val scenarioTestData = ScenarioTestData(List(createTestRecord("a", "b"), createTestRecord("c", "d")))

    val results = runTest(process, scenarioTestData)

    val contextIds = contextIdGenForFirstSource(process)
    val firstId    = contextIds.nextContextId()
    val secondId   = contextIds.nextContextId()

    results.nodeResults("filter1").toSet shouldBe Set(
      ResultContext(firstId, Map("input" -> variable(Request1("a", "b")))),
      ResultContext(secondId, Map("input" -> variable(Request1("c", "d"))))
    )

    results.invocationResults("filter1").toSet shouldBe Set(
      ExpressionInvocationResult(firstId, "expression", variable(true)),
      ExpressionInvocationResult(secondId, "expression", variable(false))
    )

    results.externalInvocationResults("processor").toSet shouldBe Set(
      ExternalInvocationResult(firstId, "processorService", variable("processor service invoked"))
    )
    results.externalInvocationResults("eagerProcessor").toSet shouldBe Set(
      ExternalInvocationResult(firstId, "collectingEager", variable("static-s-dynamic-a"))
    )

    results.externalInvocationResults("endNodeIID").toSet shouldBe Set(
      ExternalInvocationResult(firstId, "endNodeIID", variable(Response(s"alamakota-$firstId")))
    )

    RequestResponseSampleComponents.processorService.get().invocationsCount.get shouldBe 0

  }

  test("detect errors in nodes") {
    val process = ScenarioBuilder
      .requestResponse("proc1")
      .source(sourceId, "request1-post-source")
      .filter("occasionallyThrowFilter", "#input.field1() == 'a' ? 1/{0, 1}[0] == 0 : true".spel)
      .filter("filter1", "#input.field1() == 'a'".spel)
      .enricher("enricher", "var1", "enricherService")
      .processor("processor", "processorService")
      .emptySink("endNodeIID", "response-sink", "value" -> "#var1".spel)

    val scenarioTestData = ScenarioTestData(List(createTestRecord("a", "b"), createTestRecord("c", "d'")))

    val contextIds = contextIdGenForFirstSource(process)
    val firstId    = contextIds.nextContextId()
    val secondId   = contextIds.nextContextId()

    val results = runTest(process, scenarioTestData)

    results.invocationResults("occasionallyThrowFilter").toSet shouldBe Set(
      ExpressionInvocationResult(secondId, "expression", variable(true))
    )

    results.exceptions should have size 1
    results.exceptions.head.context shouldBe ResultContext(
      firstId,
      Map("input" -> variable(Request1("a", "b")))
    )
    results.exceptions.head.nodeId shouldBe Some("occasionallyThrowFilter")
    results.exceptions.head.throwable.getMessage shouldBe """Expression [#input.field1() == 'a' ? 1/{0, 1}[0] == 0 : true] evaluation failed, message: / by zero"""
  }

  test("get results on parameter sinks") {
    val process = ScenarioBuilder
      .requestResponse("proc1")
      .source(sourceId, "request1-post-source")
      .emptySink("endNodeIID", "parameterResponse-sink", "computed" -> "#input.field1()".spel)

    val scenarioTestData = ScenarioTestData(List(createTestRecord("a", "b")))
    val jobData          = JobData(process.metaData, ProcessVersion.empty.copy(processName = process.metaData.name))

    val contextIds = contextIdGenForFirstSource(process)
    val firstId    = contextIds.nextContextId()

    val results = FutureBasedRequestResponseScenarioInterpreter.testRunner.runTest(
      process = process,
      jobData = jobData,
      modelData = modelData,
      scenarioTestData = scenarioTestData,
    )

    results.nodeResults("endNodeIID").toSet shouldBe Set(
      ResultContext(firstId, Map("input" -> variable(Request1("a", "b"))))
    )

    results.externalInvocationResults("endNodeIID").toSet shouldBe Set(
      ExternalInvocationResult(firstId, "endNodeIID", variable("a withRandomString"))
    )

  }

  test("should assign unique context ids for scenario with union") {
    val branch1NodeId = "branch1"
    val branch2NodeId = "branch2"
    val process = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source(sourceId, "request1-post-source")
          .split(
            "spl",
            GraphBuilder.buildSimpleVariable("v1", "v1", "'aa'".spel).branchEnd(branch1NodeId, "union1"),
            GraphBuilder.buildSimpleVariable("v2", "v2", "'bb'".spel).branchEnd(branch2NodeId, "union1")
          ),
        GraphBuilder
          .join(
            "union1",
            "union",
            Some("unionOutput"),
            List(
              branch1NodeId -> List("Output expression" -> "{a: #v1}".spel),
              branch2NodeId -> List("Output expression" -> "{a: #v2}".spel)
            )
          )
          .customNode("collect1", "outCollector", "collect", "Input expression" -> "#unionOutput".spel)
          .emptySink("endNodeIID", "response-sink", "value" -> "#outCollector.![#this.a]".spel)
      )
    val scenarioTestData = ScenarioTestData(List(createTestRecord("a", "b")))

    val results = runTest(process, scenarioTestData)

    val sourceContextId = contextIdGenForFirstSource(process).nextContextId()
    results.nodeResults("union1") should have size 2

    val unionContextIds = results.nodeResults("union1").map(_.id)
    unionContextIds should contain only (s"$sourceContextId-$branch1NodeId", s"$sourceContextId-$branch2NodeId")
    unionContextIds should contain theSameElementsAs unionContextIds.toSet
    results.nodeResults("union1") shouldBe results.nodeResults("collect1")

    val endNodeIdInvocationResult = results.externalInvocationResults("endNodeIID").loneElement
    endNodeIdInvocationResult.contextId shouldBe contextIdGenForNodeId(process, "collect1").nextContextId()

    endNodeIdInvocationResult.value shouldBe variable(List("bb", "aa"))
  }

  private def createTestRecord(field1: String, field2: String) = {
    ScenarioTestJsonRecord(sourceId, Json.obj("field1" -> Json.fromString(field1), "field2" -> Json.fromString(field2)))
  }

  private def contextIdGenForFirstSource(scenario: CanonicalProcess): IncContextIdGenerator =
    contextIdGenForNodeId(scenario, scenario.nodes.head.id)

  private def contextIdGenForNodeId(scenario: CanonicalProcess, nodeId: String): IncContextIdGenerator =
    IncContextIdGenerator.withProcessIdNodeIdPrefix(scenario.metaData, nodeId)

  private def runTest(process: CanonicalProcess, scenarioTestData: ScenarioTestData): TestResults[Json] = {
    val jobData = JobData(process.metaData, ProcessVersion.empty.copy(processName = process.metaData.name))
    FutureBasedRequestResponseScenarioInterpreter.testRunner.runTest(
      modelData = modelData,
      jobData = jobData,
      scenarioTestData = scenarioTestData,
      process = process,
    )
  }

  private def variable(value: Any): Json = {
    def toJson(v: Any): Json = v match {
      case int: Int                               => Json.fromInt(int)
      case str: String                            => Json.fromString(str)
      case boolean: Boolean                       => Json.fromBoolean(boolean)
      case list: List[_]                          => Json.fromValues(list.map(toJson))
      case displayable: DisplayJsonWithEncoder[_] => displayable.asJson
      case any                                    => Json.fromString(any.toString)
    }

    Json.obj("pretty" -> toJson(value))
  }

}
