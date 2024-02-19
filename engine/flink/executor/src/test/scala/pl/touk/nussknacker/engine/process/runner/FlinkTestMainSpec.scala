package pl.touk.nussknacker.engine.process.runner

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.circe.Json
import org.apache.flink.runtime.client.JobExecutionException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, Inside}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestJsonRecord}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.{
  FlinkTestConfiguration,
  RecordingExceptionConsumer,
  RecordingExceptionConsumerProvider
}
import pl.touk.nussknacker.engine.graph.node.Case
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.{ModelData, spel}

import java.util.{Date, UUID}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class FlinkTestMainSpec extends AnyWordSpec with Matchers with Inside with BeforeAndAfterEach {

  import spel.Implicits._

  import scala.jdk.CollectionConverters._

  private val scenarioName      = "proc1"
  private val sourceNodeId      = "id"
  private val firstSubtaskIndex = 0

  override def beforeEach(): Unit = {
    super.beforeEach()
    MonitorEmptySink.clear()
    LogService.clear()
  }

  "A scenario run on Flink engine" when {
    "IO monad interpreter is used" should {
      runTests(useIOMonadInInterpreter = true)
    }
    "IO monad interpreter is NOT used" should {
      runTests(useIOMonadInInterpreter = false)
    }
  }

  private def runTests(useIOMonadInInterpreter: Boolean): Unit = {
    "be able to return test results" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName)
          .source(sourceNodeId, "input")
          .filter("filter1", "#input.value1 > 1")
          .buildSimpleVariable("v1", "variable1", "'ala'")
          .processor("eager1", "collectingEager", "static" -> "'s'", "dynamic" -> "#input.id")
          .processor("proc2", "logService", "all" -> "#input.id")
          .emptySink("out", "valueMonitor", "Value" -> "#input.value1")

      val input  = SimpleRecord("0", 1, "2", new Date(3), Some(4), 5, "6")
      val input2 = SimpleRecord("0", 11, "2", new Date(3), Some(4), 5, "6")

      val results = runFlinkTest(
        process,
        ScenarioTestData(
          List(
            ScenarioTestJsonRecord(sourceNodeId, Json.fromString("0|1|2|3|4|5|6")),
            ScenarioTestJsonRecord(sourceNodeId, Json.fromString("0|11|2|3|4|5|6"))
          )
        ),
        useIOMonadInInterpreter
      )

      val nodeResults = results.nodeResults

      nodeResults(sourceNodeId) shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))
      nodeResults("filter1") shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))
      nodeResults("v1") shouldBe List(nodeResult(1, "input" -> input2))
      nodeResults("proc2") shouldBe List(nodeResult(1, "input" -> input2, "variable1" -> "ala"))
      nodeResults("out") shouldBe List(nodeResult(1, "input" -> input2, "variable1" -> "ala"))

      val invocationResults = results.invocationResults

      invocationResults("proc2") shouldBe
        List(ExpressionInvocationResult(s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-1", "all", "0"))

      invocationResults("out") shouldBe
        List(ExpressionInvocationResult(s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-1", "Value", 11))

      results.externalInvocationResults("proc2") shouldBe List(
        ExternalInvocationResult(
          s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-1",
          "logService",
          "0-collectedDuringServiceInvocation"
        )
      )
      results.externalInvocationResults("out") shouldBe List(
        ExternalInvocationResult(s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-1", "valueMonitor", 11)
      )
      results.externalInvocationResults("eager1") shouldBe List(
        ExternalInvocationResult(
          s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-1",
          "collectingEager",
          "static-s-dynamic-0"
        )
      )

      MonitorEmptySink.invocationsCount.get() shouldBe 0
      LogService.invocationsCount.get() shouldBe 0
    }

    "collect results for split" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName)
          .source(sourceNodeId, "input")
          .split("splitId1", GraphBuilder.emptySink("out1", "monitor"), GraphBuilder.emptySink("out2", "monitor"))

      val results = runFlinkTest(
        process,
        ScenarioTestData(List(createTestRecord(), createTestRecord(value1 = 11))),
        useIOMonadInInterpreter
      )

      results.nodeResults("splitId1") shouldBe List(
        nodeResult(
          0,
          "input" ->
            SimpleRecord("0", 1, "2", new Date(3), Some(4), 5, "6")
        ),
        nodeResult(
          1,
          "input" ->
            SimpleRecord("0", 11, "2", new Date(3), Some(4), 5, "6")
        )
      )
    }

    "return correct result for custom node" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName)
          .source(sourceNodeId, "input")
          .customNode("cid", "out", "stateCustom", "groupBy" -> "#input.id", "stringVal" -> "'s'")
          .emptySink("out", "valueMonitor", "Value" -> "#input.value1 + ' ' + #out.previous")

      val input  = SimpleRecord("0", 1, "2", new Date(3), Some(4), 5, "6")
      val input2 = SimpleRecord("0", 11, "2", new Date(3), Some(4), 5, "6")

      val aggregate  = SimpleRecordWithPreviousValue(input, 0, "s")
      val aggregate2 = SimpleRecordWithPreviousValue(input2, 1, "s")

      val results = runFlinkTest(
        process,
        ScenarioTestData(List(createTestRecord(), createTestRecord(value1 = 11))),
        useIOMonadInInterpreter
      )

      val nodeResults = results.nodeResults

      nodeResults(sourceNodeId) shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))
      nodeResults("cid") shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))
      nodeResults("out") shouldBe List(
        nodeResult(0, "input" -> input, "out"  -> aggregate),
        nodeResult(1, "input" -> input2, "out" -> aggregate2)
      )

      val invocationResults = results.invocationResults

      invocationResults("cid") shouldBe
        List(
          // we record only LazyParameter execution results
          ExpressionInvocationResult(s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-0", "groupBy", "0"),
          ExpressionInvocationResult(s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-1", "groupBy", "0")
        )

      invocationResults("out") shouldBe
        List(
          ExpressionInvocationResult(s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-0", "Value", "1 0"),
          ExpressionInvocationResult(s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-1", "Value", "11 1")
        )

      results.externalInvocationResults("out") shouldBe
        List(
          ExternalInvocationResult(s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-0", "valueMonitor", "1 0"),
          ExternalInvocationResult(s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-1", "valueMonitor", "11 1")
        )
    }

    "handle large parallelism" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName)
          .parallelism(4)
          .source(sourceNodeId, "input")
          .emptySink("out", "monitor")

      val results =
        runFlinkTest(
          process,
          ScenarioTestData(createTestRecord() :: List.fill(4)(createTestRecord(value1 = 11))),
          useIOMonadInInterpreter
        )

      val nodeResults = results.nodeResults

      nodeResults(sourceNodeId) should have length 5

    }

    "detect errors" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName)
          .source(sourceNodeId, "input")
          .processor("failing", "throwingService", "throw" -> "#input.value1 == 2")
          .filter("filter", "1 / #input.value1 >= 0")
          .emptySink("out", "monitor")

      val results = runFlinkTest(
        process,
        ScenarioTestData(
          List(
            createTestRecord(id = "0", value1 = 1),
            createTestRecord(id = "1", value1 = 0),
            createTestRecord(id = "2", value1 = 2),
            createTestRecord(id = "3", value1 = 4)
          )
        ),
        useIOMonadInInterpreter
      )

      val nodeResults = results.nodeResults

      nodeResults(sourceNodeId) should have length 4
      nodeResults("out") should have length 2

      results.exceptions should have length 2

      val exceptionFromExpression = results.exceptions.head
      exceptionFromExpression.nodeComponentInfo.map(_.nodeId) shouldBe Some("filter")
      exceptionFromExpression.context.variables("input").asInstanceOf[SimpleRecord].id shouldBe "1"
      exceptionFromExpression.throwable.getMessage shouldBe "Expression [1 / #input.value1 >= 0] evaluation failed, message: / by zero"

      val exceptionFromService = results.exceptions.last
      exceptionFromService.nodeComponentInfo.map(_.nodeId) shouldBe Some("failing")
      exceptionFromService.context.variables("input").asInstanceOf[SimpleRecord].id shouldBe "2"
      exceptionFromService.throwable.getMessage shouldBe "Thrown as expected"
    }

    "ignore real exception handler" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName)
          .source(sourceNodeId, "input")
          .processor("failing", "throwingService", "throw" -> "#input.value1 == 2")
          .filter("filter", "1 / #input.value1 >= 0")
          .emptySink("out", "monitor")

      val exceptionConsumerId = UUID.randomUUID().toString
      val results = runFlinkTest(
        process = process,
        scenarioTestData = ScenarioTestData(
          List(
            createTestRecord(id = "0", value1 = 1),
            createTestRecord(id = "1", value1 = 0),
            createTestRecord(id = "2", value1 = 2),
            createTestRecord(id = "3", value1 = 4)
          )
        ),
        useIOMonadInInterpreter,
        enrichDefaultConfig = RecordingExceptionConsumerProvider.configWithProvider(_, exceptionConsumerId)
      )

      val nodeResults = results.nodeResults

      nodeResults(sourceNodeId) should have length 4
      nodeResults("out") should have length 2

      results.exceptions should have length 2
      RecordingExceptionConsumer.exceptionsFor(exceptionConsumerId) shouldBe Symbol("empty")
    }

    "handle transient errors" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName)
          .source(sourceNodeId, "input")
          .processor("failing", "throwingTransientService", "throw" -> "#input.value1 == 2")
          .emptySink("out", "monitor")

      val run = Future {
        runFlinkTest(process, ScenarioTestData(List(createTestRecord(id = "2", value1 = 2))), useIOMonadInInterpreter)
      }

      intercept[JobExecutionException](Await.result(run, 10 seconds))
    }

    "handle json input" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName)
          .source(sourceNodeId, "jsonInput")
          .emptySink("out", "valueMonitor", "Value" -> "#input")
      val testData = ScenarioTestData(
        List(
          ScenarioTestJsonRecord(
            sourceNodeId,
            Json.obj(sourceNodeId -> Json.fromString("1"), "field" -> Json.fromString("11"))
          ),
          ScenarioTestJsonRecord(
            sourceNodeId,
            Json.obj(sourceNodeId -> Json.fromString("2"), "field" -> Json.fromString("22"))
          ),
          ScenarioTestJsonRecord(
            sourceNodeId,
            Json.obj(sourceNodeId -> Json.fromString("3"), "field" -> Json.fromString("33"))
          ),
        )
      )

      val results = runFlinkTest(process, testData, useIOMonadInInterpreter)

      results.nodeResults(sourceNodeId) should have size 3
      results.externalInvocationResults("out") shouldBe
        List(
          ExternalInvocationResult(
            s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-0",
            "valueMonitor",
            SimpleJsonRecord("1", "11")
          ),
          ExternalInvocationResult(
            s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-1",
            "valueMonitor",
            SimpleJsonRecord("2", "22")
          ),
          ExternalInvocationResult(
            s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-2",
            "valueMonitor",
            SimpleJsonRecord("3", "33")
          )
        )
    }

    "handle custom variables in source" in {
      val process = ScenarioBuilder
        .streaming(scenarioName)
        .source(sourceNodeId, "genericSourceWithCustomVariables", "elements" -> "{'abc'}")
        .emptySink("out", "valueMonitor", "Value" -> "#additionalOne + '|' + #additionalTwo")
      val testData = ScenarioTestData(List(ScenarioTestJsonRecord(sourceNodeId, Json.fromString("abc"))))

      val results = runFlinkTest(process, testData, useIOMonadInInterpreter)

      results.nodeResults(sourceNodeId) should have size 1
      results.externalInvocationResults("out") shouldBe
        List(
          ExternalInvocationResult(
            s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-0",
            "valueMonitor",
            "transformed:abc|3"
          )
        )
    }

    "give meaningful error messages for sink errors" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName)
          .source(sourceNodeId, "input")
          .emptySink("out", "sinkForInts", "Value" -> "15 / {0, 1}[0]")

      val results =
        runFlinkTest(process, ScenarioTestData(List(createTestRecord(id = "2", value1 = 2))), useIOMonadInInterpreter)

      results.exceptions should have length 1
      results.exceptions.head.nodeComponentInfo.map(_.nodeId) shouldBe Some("out")
      results.exceptions.head.throwable.getMessage should include("message: / by zero")

      SimpleProcessConfigCreator.sinkForIntsResultsHolder.results should have length 0
    }

    "be able to test process with time windows" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName)
          .source(sourceNodeId, "input")
          .customNode("cid", "count", "transformWithTime", "seconds" -> "10")
          .emptySink("out", "monitor")

      def recordWithSeconds(duration: FiniteDuration) =
        ScenarioTestJsonRecord(sourceNodeId, Json.fromString(s"0|0|0|${duration.toMillis}|0|0|0"))

      val results = runFlinkTest(
        process,
        ScenarioTestData(
          List(
            recordWithSeconds(1 second),
            recordWithSeconds(2 second),
            recordWithSeconds(5 second),
            recordWithSeconds(9 second),
            recordWithSeconds(20 second)
          )
        ),
        useIOMonadInInterpreter
      )

      val nodeResults = results.nodeResults

      nodeResults("out").map(_.variables) shouldBe List(Map("count" -> 4), Map("count" -> 1))

    }

    "be able to test typed map" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName)
          .source(sourceNodeId, "typedJsonInput", "type" -> """{"field1": "String", "field2": "java.lang.String"}""")
          .emptySink("out", "valueMonitor", "Value" -> "#input.field1 + #input.field2")

      val results = runFlinkTest(
        process,
        ScenarioTestData(
          ScenarioTestJsonRecord(
            sourceNodeId,
            Json.obj("field1" -> Json.fromString("abc"), "field2" -> Json.fromString("def"))
          ) :: Nil
        ),
        useIOMonadInInterpreter
      )

      results.invocationResults("out").map(_.value) shouldBe List("abcdef")
    }

    "using dependent services" in {
      val countToPass   = 15
      val valueToReturn = 18

      val process = ScenarioBuilder
        .streaming(scenarioName)
        .source(sourceNodeId, "input")
        .enricher(
          "dependent",
          "parsed",
          "returningDependentTypeService",
          "definition" -> "{'field1', 'field2'}",
          "toFill"     -> "#input.value1.toString()",
          "count"      -> countToPass.toString
        )
        .emptySink("out", "valueMonitor", "Value" -> "#parsed.size + ' ' + #parsed[0].field2")

      val results =
        runFlinkTest(process, ScenarioTestData(List(createTestRecord(value1 = valueToReturn))), useIOMonadInInterpreter)

      results.invocationResults("out").map(_.value) shouldBe List(s"$countToPass $valueToReturn")
    }

    "switch value should be equal to variable value" in {
      import spel.Implicits._
      val process = ScenarioBuilder
        .streaming(scenarioName)
        .parallelism(1)
        .source(sourceNodeId, "input")
        .switch(
          "switch",
          "#input.id == 'ala'",
          "output",
          Case(
            "#output == false",
            GraphBuilder.emptySink("out", "valueMonitor", "Value" -> "'any'")
          )
        )

      val recordTrue  = createTestRecord(id = "ala")
      val recordFalse = createTestRecord(id = "bela")

      val results = runFlinkTest(process, ScenarioTestData(List(recordTrue, recordFalse)), useIOMonadInInterpreter)

      val invocationResults = results.invocationResults

      invocationResults("switch").filter(_.name == "expression").head.value shouldBe true
      invocationResults("switch").filter(_.name == "expression").last.value shouldBe false
      // first record was filtered out
      invocationResults("out").head.contextId shouldBe s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-1"
    }

    "should handle joins for one input (diamond-like) " in {
      val process = ScenarioBuilder
        .streaming(scenarioName)
        .sources(
          GraphBuilder
            .source(sourceNodeId, "input")
            .split(
              "split",
              GraphBuilder.filter("left", "#input.id != 'a'").branchEnd("end1", "join1"),
              GraphBuilder.filter("right", "#input.id != 'b'").branchEnd("end2", "join1")
            ),
          GraphBuilder
            .join(
              "join1",
              "joinBranchExpression",
              Some("input33"),
              List(
                "end1" -> List("value" -> "#input"),
                "end2" -> List("value" -> "#input")
              )
            )
            .processorEnd("proc2", "logService", "all" -> "#input33.id")
        )

      val recA = createTestRecord(id = "a")
      val recB = createTestRecord(id = "b")
      val recC = createTestRecord(id = "c")

      val results = runFlinkTest(process, ScenarioTestData(List(recA, recB, recC)), useIOMonadInInterpreter)

      results.invocationResults("proc2").map(_.contextId) should contain only (
        s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-1-end1",
        s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-2-end1",
        s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-0-end2",
        s"$scenarioName-$sourceNodeId-$firstSubtaskIndex-2-end2"
      )
      results.externalInvocationResults("proc2").map(_.value.asInstanceOf[String]).sorted shouldBe List(
        "a",
        "b",
        "c",
        "c"
      ).map(_ + "-collectedDuringServiceInvocation")
    }

    "should test multiple source scenario" in {
      val process = ScenarioBuilder
        .streaming(scenarioName)
        .sources(
          GraphBuilder
            .source("source1", "input")
            .filter("filter1", "#input.id != 'a'")
            .branchEnd("end1", "join"),
          GraphBuilder
            .source("source2", "input")
            .filter("filter2", "#input.id != 'b'")
            .branchEnd("end2", "join"),
          GraphBuilder
            .join(
              "join",
              "joinBranchExpression",
              Some("joinInput"),
              List(
                "end1" -> List("value" -> "#input"),
                "end2" -> List("value" -> "#input")
              )
            )
            .processorEnd("proc2", "logService", "all" -> "#joinInput.id")
        )
      val scenarioTestData = ScenarioTestData(
        List(
          createTestRecord(sourceId = "source1", id = "a"),
          createTestRecord(sourceId = "source2", id = "a"),
          createTestRecord(sourceId = "source1", id = "d"),
          createTestRecord(sourceId = "source2", id = "b"),
          createTestRecord(sourceId = "source2", id = "c"),
        )
      )
      val recordA = SimpleRecord("a", 1, "2", new Date(3), Some(4), 5, "6")
      val recordB = recordA.copy(id = "b")
      val recordC = recordA.copy(id = "c")
      val recordD = recordA.copy(id = "d")

      val results = runFlinkTest(process, scenarioTestData, useIOMonadInInterpreter)

      val nodeResults = results.nodeResults
      nodeResults("source1") shouldBe List(
        nodeResult(0, "source1", "input" -> recordA),
        nodeResult(1, "source1", "input" -> recordD)
      )
      nodeResults("source2") shouldBe List(
        nodeResult(0, "source2", "input" -> recordA),
        nodeResult(1, "source2", "input" -> recordB),
        nodeResult(2, "source2", "input" -> recordC)
      )
      nodeResults("filter1") shouldBe nodeResults("source1")
      nodeResults("filter2") shouldBe nodeResults("source2")
      nodeResults("$edge-end1-join") shouldBe List(nodeResult(1, "source1", "input" -> recordD))
      nodeResults("$edge-end2-join") shouldBe List(
        nodeResult(0, "source2", "input" -> recordA),
        nodeResult(2, "source2", "input" -> recordC)
      )
      nodeResults("join") should contain only (
        nodeResult(1, "source1", "end1", "input" -> recordD, "joinInput" -> recordD),
        nodeResult(0, "source2", "end2", "input" -> recordA, "joinInput" -> recordA),
        nodeResult(2, "source2", "end2", "input" -> recordC, "joinInput" -> recordC)
      )

      results.invocationResults("proc2") should contain only (
        ExpressionInvocationResult(s"$scenarioName-source1-$firstSubtaskIndex-1-end1", "all", "d"),
        ExpressionInvocationResult(s"$scenarioName-source2-$firstSubtaskIndex-0-end2", "all", "a"),
        ExpressionInvocationResult(s"$scenarioName-source2-$firstSubtaskIndex-2-end2", "all", "c")
      )

      results
        .externalInvocationResults("proc2")
        .map(_.value.asInstanceOf[String])
        .sorted should contain theSameElementsAs List("a", "c", "d").map(_ + "-collectedDuringServiceInvocation")
    }

    "should have correct run mode" in {
      val process = ScenarioBuilder
        .streaming(scenarioName)
        .source("start", "input")
        .enricher("componentUseCaseService", "componentUseCaseService", "returningComponentUseCaseService")
        .customNode("componentUseCaseCustomNode", "componentUseCaseCustomNode", "transformerAddingComponentUseCase")
        .emptySink("out", "valueMonitor", "Value" -> "{#componentUseCaseService, #componentUseCaseCustomNode}")

      val results =
        runFlinkTest(process, ScenarioTestData(List(createTestRecord(sourceId = "start"))), useIOMonadInInterpreter)

      results.invocationResults("out").map(_.value) shouldBe List(
        List(ComponentUseCase.TestRuntime, ComponentUseCase.TestRuntime).asJava
      )
    }
  }

  private def createTestRecord(
      sourceId: String = sourceNodeId,
      id: String = "0",
      value1: Long = 1
  ): ScenarioTestJsonRecord =
    ScenarioTestJsonRecord(sourceId, Json.fromString(s"$id|$value1|2|3|4|5|6"))

  private def runFlinkTest(
      process: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
      useIOMonadInInterpreter: Boolean,
      enrichDefaultConfig: Config => Config = identity
  ): TestResults = {
    val config = enrichDefaultConfig(ConfigFactory.load("application.conf"))
      .withValue("globalParameters.useIOMonadInInterpreter", ConfigValueFactory.fromAnyRef(useIOMonadInInterpreter))

    // We need to set context loader to avoid forking in sbt
    val modelData = ModelData.duringFlinkExecution(config)
    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      FlinkTestMain.run(modelData, process, scenarioTestData, FlinkTestConfiguration.configuration())
    }
  }

  private def nodeResult(count: Int, vars: (String, Any)*): Context =
    nodeResult(count, sourceNodeId, vars: _*)

  private def nodeResult(count: Int, sourceId: String, vars: (String, Any)*): Context =
    Context(s"$scenarioName-$sourceId-$firstSubtaskIndex-$count", Map(vars: _*))

  private def nodeResult(
      count: Int,
      sourceId: String,
      branchId: String,
      vars: (String, Any)*
  ): Context =
    Context(s"$scenarioName-$sourceId-$firstSubtaskIndex-$count-$branchId", Map(vars: _*))

}
