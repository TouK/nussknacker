package pl.touk.nussknacker.engine.requestresponse.test

import com.typesafe.config.ConfigFactory
import io.circe.Json
import org.scalatest.BeforeAndAfterEach
import org.scalatest.LoneElement._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{
  ContextId,
  ContextIdPathPart,
  DisplayJsonWithEncoder,
  JobData,
  NodeId,
  ProcessVersion
}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.runtimecontext.IncContextIdGenerator
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestSourceSpecificFormatJsonRecord}
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
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.engine.testmode.TestProcess._

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._

class RequestResponseTestMainSpec extends AnyFunSuite with Matchers with BeforeAndAfterEach {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  val requestResponseSampleComponents = new RequestResponseSampleComponents

  private val modelData = LocalModelData(
    ConfigFactory.load(),
    requestResponseSampleComponents.components :::
      LiteBaseComponentProvider.Components :::
      RequestResponseComponentProvider.Components
  )

  private val sourceId = NodeId("start")

  private val mockedTimestamp = Instant.now()

  test("perform test on mocks") {
    val process = ScenarioBuilder
      .requestResponse("proc1")
      .source(sourceId.value, "request1-post-source")
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

    nodeResults(results, "filter1").toSet shouldBe Set(
      (firstId, Map("input" -> variable(Request1("a", "b")))),
      (secondId, Map("input" -> variable(Request1("c", "d"))))
    )

    results.expressionEvaluationResults(NodeId("filter1")).map(withMockedTimestamp).toSet shouldBe Set(
      ExpressionEvaluationResult(firstId, mockedTimestamp, "expression", variable(true)),
      ExpressionEvaluationResult(secondId, mockedTimestamp, "expression", variable(false))
    )

    results.externalServiceInvocationResults(NodeId("processor")).map(withMockedTimestamp).toSet shouldBe Set(
      ExternalServiceInvocationResult(
        firstId,
        mockedTimestamp,
        "processorService",
        variable("processor service invoked")
      )
    )
    results.externalServiceInvocationResults(NodeId("eagerProcessor")).map(withMockedTimestamp).toSet shouldBe Set(
      ExternalServiceInvocationResult(firstId, mockedTimestamp, "collectingEager", variable("static-s-dynamic-a"))
    )

    results.externalServiceInvocationResults(NodeId("endNodeIID")).map(withMockedTimestamp).toSet shouldBe Set(
      ExternalServiceInvocationResult(firstId, mockedTimestamp, "endNodeIID", variable(Response(s"alamakota-$firstId")))
    )

    RequestResponseSampleComponents.processorService.get().invocationsCount.get shouldBe 0

  }

  test("detect errors in nodes") {
    val process = ScenarioBuilder
      .requestResponse("proc1")
      .source(sourceId.value, "request1-post-source")
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

    results.expressionEvaluationResults(NodeId("occasionallyThrowFilter")).map(withMockedTimestamp).toSet shouldBe Set(
      ExpressionEvaluationResult(secondId, mockedTimestamp, "expression", variable(true))
    )

    results.exceptions should have size 1
    results.exceptions.head.context.id shouldBe firstId
    results.exceptions.head.context.variables shouldBe Map("input" -> variable(Request1("a", "b")))
    results.exceptions.head.nodeId shouldBe Some(NodeId("occasionallyThrowFilter"))
    results.exceptions.head.throwable.getMessage shouldBe """Expression [#input.field1() == 'a' ? 1/{0, 1}[0] == 0 : true] evaluation failed, message: / by zero"""
  }

  test("get results on parameter sinks") {
    val process = ScenarioBuilder
      .requestResponse("proc1")
      .source(sourceId.value, "request1-post-source")
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

    nodeResults(results, "endNodeIID").toSet shouldBe Set(
      (firstId, Map("input" -> variable(Request1("a", "b"))))
    )

    results.externalServiceInvocationResults(NodeId("endNodeIID")).map(withMockedTimestamp).toSet shouldBe Set(
      ExternalServiceInvocationResult(firstId, mockedTimestamp, "endNodeIID", variable("a withRandomString"))
    )

  }

  test("should assign unique context ids for scenario with union") {
    val branch1NodeId = "branch1"
    val branch2NodeId = "branch2"
    val process = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source(sourceId.value, "request1-post-source")
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
    results.nodeResults(NodeId("union1")) should have size 2

    val unionContextIds = results.nodeResults(NodeId("union1")).map(_.id)
    unionContextIds should contain only (
      sourceContextId.copy(
        contextIdPath = List(
          ContextIdPathPart(NodeId("spl"), "v1"),
          ContextIdPathPart(NodeId("union1"), branch1NodeId),
        ).asJava
      ),
      sourceContextId.copy(
        contextIdPath = List(
          ContextIdPathPart(NodeId("spl"), "v2"),
          ContextIdPathPart(NodeId("union1"), branch2NodeId),
        ).asJava
      ),
    )
    unionContextIds should contain theSameElementsAs unionContextIds.toSet
    nodeResults(results, "union1") shouldBe nodeResults(results, "collect1")

    val endNodeIdInvocationResult = results.externalServiceInvocationResults(NodeId("endNodeIID")).loneElement
    endNodeIdInvocationResult.contextId shouldBe contextIdGenForNodeId(process, NodeId("collect1")).nextContextId()

    endNodeIdInvocationResult.value shouldBe variable(List("aa", "bb"))
  }

  test("scenario with multiple unions should work properly") {
    val process = ScenarioBuilder
      .requestResponse("unionTest", "unionTest")
      .additionalFields(properties =
        Map(
          "inputSchema" -> """{"type": "object", "properties": {"field1": {"type": "string"}, "field2": {"type": "string"}}}""",
          "outputSchema" -> "{}"
        )
      )
      .sources(
        GraphBuilder
          .source("start", "request")
          .filter(
            "filter",
            "#input.field1 == 'right'".spel,
            nextFalse = {
              GraphBuilder
                .buildSimpleVariable("variable 1", "var1", "1".spel)
                .split(
                  "Split",
                  GraphBuilder
                    .buildSimpleVariable("leftVariableB", "leftVariableB", "'leftVariableB'".spel)
                    .branchEnd("leftVariableB", "unionLeft"),
                  GraphBuilder
                    .buildSimpleVariable("leftVariableA", "leftVariableA", "'leftVariableA'".spel)
                    .branchEnd("leftVariableA", "unionLeft"),
                )
            }
          )
          .filter(
            "filter 1",
            "true".spel,
            nextFalse = {
              GraphBuilder
                .buildSimpleVariable("rightVariableA", "rightVariableA", "'rightVariableA'".spel)
                .branchEnd("rightVariableA", "unionRight")
            }
          )
          .buildSimpleVariable("rightVariableB", "rightVariableB", "'rightVariableB'".spel)
          .branchEnd("rightVariableB", "unionRight"),
        GraphBuilder
          .join(
            "unionLeft",
            "union",
            Some("unionLeft"),
            branchParams = List(
              "leftVariableA" -> List("Output expression" -> "#leftVariableA".spel),
              "leftVariableB" -> List("Output expression" -> "#leftVariableB".spel)
            )
          )
          .branchEnd("unionLeft", "unionBeforeEnd"),
        GraphBuilder
          .join(
            "unionRight",
            "union",
            Some("unionRight"),
            branchParams = List(
              "rightVariableA" -> List("Output expression" -> "#rightVariableA".spel),
              "rightVariableB" -> List("Output expression" -> "#rightVariableB".spel)
            )
          )
          .branchEnd("unionRight", "unionBeforeEnd"),
        GraphBuilder
          .join(
            "unionBeforeEnd",
            "union",
            Some("unionBeforeEnd"),
            branchParams = List(
              "unionRight" -> List("Output expression" -> "#unionRight".spel),
              "unionLeft"  -> List("Output expression" -> "#unionLeft".spel)
            )
          )
          .emptySink("Response", "response", "Raw editor" -> "false".spel, "Value" -> "#unionBeforeEnd".spel),
      )

    def prepareContextId(varValue: String, right: Boolean): ContextId = {
      val unionName = if (right) "unionRight" else "unionLeft"
      val splitPart = if (right) Nil else List(ContextIdPathPart(NodeId("Split"), varValue))
      val contextIdPath = splitPart ++ List(
        ContextIdPathPart(NodeId(unionName), varValue),
        ContextIdPathPart(NodeId("unionBeforeEnd"), unionName)
      )
      ContextId(ProcessName("unionTest"), NodeId("start"), 0, 0, contextIdPath)
    }

    // Scenario go to 'left' nodes, where we have split and later union -> hence two variables
    val leftUnionResults = runTest(process, ScenarioTestData(List(createTestRecord("left", ""))))

    leftUnionResults.nodeResults.get(NodeId("unionRight")) should not be defined
    leftUnionResults.nodeResults(NodeId("$edge-leftVariableA-unionLeft")) should have size 1
    leftUnionResults.nodeResults(NodeId("$edge-leftVariableB-unionLeft")) should have size 1
    leftUnionResults.externalServiceInvocationResults(NodeId("Response")).map(withMockedTimestamp).toSet shouldBe Set(
      ExternalServiceInvocationResult(
        prepareContextId("leftVariableB", right = false),
        mockedTimestamp,
        "Response",
        variable("leftVariableB")
      ),
      ExternalServiceInvocationResult(
        prepareContextId("leftVariableA", right = false),
        mockedTimestamp,
        "Response",
        variable("leftVariableA")
      )
    )

    val rightUnionResults = runTest(process, ScenarioTestData(List(createTestRecord("right", ""))))

    // Scenario go to 'right' nodes, where we have filter and later union -> hence one variable (B)
    rightUnionResults.nodeResults.get(NodeId("unionLeft")) should not be defined
    rightUnionResults.nodeResults(NodeId("$edge-rightVariableB-unionRight")) should have size 1
    rightUnionResults.externalServiceInvocationResults(NodeId("Response")).map(withMockedTimestamp).toSet shouldBe Set(
      ExternalServiceInvocationResult(
        prepareContextId("rightVariableB", right = true),
        mockedTimestamp,
        "Response",
        variable("rightVariableB")
      ),
    )

  }

  private def createTestRecord(field1: String, field2: String) = {
    ScenarioTestSourceSpecificFormatJsonRecord(
      sourceId,
      Json.obj("field1" -> Json.fromString(field1), "field2" -> Json.fromString(field2))
    )
  }

  private def contextIdGenForFirstSource(scenario: CanonicalProcess): IncContextIdGenerator =
    contextIdGenForNodeId(scenario, scenario.nodes.head.id)

  private def contextIdGenForNodeId(scenario: CanonicalProcess, nodeId: NodeId): IncContextIdGenerator =
    IncContextIdGenerator.withProcessIdNodeIdPrefix(scenario.metaData, nodeId, taskId = 0)

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

  private def nodeResults[T](results: TestProcess.TestResults[T], nodeId: String) =
    results.nodeResults(NodeId(nodeId)).map(r => (r.id, r.variables))

  private def withMockedTimestamp(result: ExpressionEvaluationResult[Json]) =
    result.copy(timestamp = mockedTimestamp)

  private def withMockedTimestamp(result: ExternalServiceInvocationResult[Json]) =
    result.copy(timestamp = mockedTimestamp)

}
