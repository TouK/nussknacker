package pl.touk.nussknacker.engine.flink.minicluster.scenariotesting

import cats.effect.unsafe.IORuntime
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.circe.Json
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Inside, OptionValues}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  DesignerWideComponentId,
  ParameterAdditionalUIConfig
}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.IncompatibleParameterDefinitionModification
import pl.touk.nussknacker.engine.api.context.ScenarioCompilationErrors
import pl.touk.nussknacker.engine.api.definition.{SpelParameterEditor, SpelTemplateParameterEditor}
import pl.touk.nussknacker.engine.api.parameter.{
  ParameterName,
  ParameterValueCompileTimeValidation,
  ValueInputWithDictEditor
}
import pl.touk.nussknacker.engine.api.process.{ComponentUseContext, ProcessName}
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestSourceSpecificFormatJsonRecord}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.classloader.ModelClassLoader
import pl.touk.nussknacker.engine.compile.FragmentResolver
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.minicluster.MiniClusterJobStatusCheckingOps.JobStateCheckError
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.FlinkMiniClusterScenarioTestRunnerSpec.{
  fragmentWithValidationName,
  processWithFragmentParameterValidation
}
import pl.touk.nussknacker.engine.flink.minicluster.util.DurationToRetryPolicyConverter
import pl.touk.nussknacker.engine.flink.minicluster.util.DurationToRetryPolicyConverterOps._
import pl.touk.nussknacker.engine.flink.test.{RecordingExceptionConsumer, RecordingExceptionConsumerProvider}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{Case, FragmentInputDefinition, FragmentOutputDefinition}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.process.runner.SimpleProcessConfigCreator
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.time.Instant
import java.util.{Date, UUID}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions

import Expression.Language.DictKeyWithLabel

class FlinkMiniClusterScenarioTestRunnerSpec
    extends AnyWordSpec
    with Matchers
    with Inside
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with OptionValues
    with VeryPatientScalaFutures {

  import pl.touk.nussknacker.engine.spel.SpelExtension._
  import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val ioRuntime: IORuntime = IORuntime.global

  private val scenarioName      = ProcessName("proc1")
  private val sourceNodeId      = NodeId("id")
  private val firstSubtaskIndex = 0

  private val miniClusterWithServices = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  private val mockedTimestamp = Instant.now()

  override def beforeEach(): Unit = {
    super.beforeEach()
    MonitorEmptySink.clear()
    LogService.clear()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    miniClusterWithServices.close()
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
    "should wait configured amount of time for scenario finishing and cancel job if it is not finished during this time" in {
      val sleepSecondsInScenario = (patienceConfig.timeout * 2).toSeconds
      val process =
        ScenarioBuilder
          .streaming(scenarioName.value)
          .source(sourceNodeId.id, "input")
          .customNodeNoOutput("sleep", "sleep", "seconds" -> sleepSecondsInScenario.toString.spel)
          .emptySink("out", "valueMonitor", "Value" -> "#input.value1".spel)

      val testRunner =
        prepareTestRunner(useIOMonadInInterpreter, waitForJobIsFinishedRetryPolicy = 0.seconds.toPausePolicy)

      def runTests = testRunner
        .runTests(
          process,
          ScenarioTestData(
            List(
              ScenarioTestSourceSpecificFormatJsonRecord(sourceNodeId, Json.fromString("0|11|2|3|4|5|6"))
            )
          )
        )
        // We have to wait a shorter time than scenario total time to show limiting of scenario execution time
        .futureValue(Timeout((sleepSecondsInScenario - 1).seconds))

      intercept[TestFailedException](runTests) should matchPattern {
        case ex: TestFailedException if ex.getCause.isInstanceOf[JobStateCheckError] =>
      }
    }

    "be able to return test results" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName.value)
          .source(sourceNodeId.id, "input")
          .filter("filter1", "#input.value1 > 1".spel)
          .buildSimpleVariable("v1", "variable1", "'ala'".spel)
          .processor("eager1", "collectingEager", "static" -> "'s'".spel, "dynamic" -> "#input.id".spel)
          .processor("proc2", "logService", "all" -> "#input.id".spel)
          .emptySink("out", "valueMonitor", "Value" -> "#input.value1".spel)

      val input  = SimpleRecord("0", 1, "2", new Date(3), Some(4), 5, "6")
      val input2 = SimpleRecord("0", 11, "2", new Date(3), Some(4), 5, "6")

      val results = prepareTestRunner(useIOMonadInInterpreter)
        .runTests(
          process,
          ScenarioTestData(
            List(
              ScenarioTestSourceSpecificFormatJsonRecord(sourceNodeId, Json.fromString("0|1|2|3|4|5|6")),
              ScenarioTestSourceSpecificFormatJsonRecord(sourceNodeId, Json.fromString("0|11|2|3|4|5|6"))
            )
          )
        )
        .futureValue

      val nodeResults = nodeResultsWithoutTimestamp(results)

      nodeResults(sourceNodeId) shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))
      nodeResults(NodeId("filter1")) shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))
      nodeResults(NodeId("v1")) shouldBe List(nodeResult(1, "input" -> input2))
      nodeResults(NodeId("proc2")) shouldBe List(nodeResult(1, "input" -> input2, "variable1" -> "ala"))
      nodeResults(NodeId("out")) shouldBe List(nodeResult(1, "input" -> input2, "variable1" -> "ala"))

      val expressionEvaluationResults = results.expressionEvaluationResults

      expressionEvaluationResults(NodeId("proc2")).map(withMockedTimestamp) shouldBe
        List(
          ExpressionEvaluationResult(
            ContextId(
              scenarioName = scenarioName,
              originatingNodeId = sourceNodeId,
              taskId = firstSubtaskIndex,
              index = 1
            ),
            mockedTimestamp,
            "all",
            variable("0")
          )
        )

      expressionEvaluationResults(NodeId("out")).map(withMockedTimestamp) shouldBe
        List(
          ExpressionEvaluationResult(
            ContextId(
              scenarioName = scenarioName,
              originatingNodeId = sourceNodeId,
              taskId = firstSubtaskIndex,
              index = 1
            ),
            mockedTimestamp,
            "Value",
            variable(11)
          )
        )

      results.externalServiceInvocationResults(NodeId("proc2")).map(r => (r.contextId, r.name, r.value)) shouldBe List(
        (
          ContextId(
            scenarioName = scenarioName,
            originatingNodeId = sourceNodeId,
            taskId = firstSubtaskIndex,
            index = 1
          ),
          "logService",
          variable("0-collectedDuringServiceInvocation")
        )
      )

      results.externalServiceInvocationResults(NodeId("out")).map(withMockedTimestamp) shouldBe List(
        ExternalServiceInvocationResult(
          ContextId(
            scenarioName = scenarioName,
            originatingNodeId = sourceNodeId,
            taskId = firstSubtaskIndex,
            index = 1
          ),
          mockedTimestamp,
          "valueMonitor",
          variable(11)
        )
      )

      results.externalServiceInvocationResults(NodeId("eager1")).map(withMockedTimestamp) shouldBe List(
        ExternalServiceInvocationResult(
          ContextId(
            scenarioName = scenarioName,
            originatingNodeId = sourceNodeId,
            taskId = firstSubtaskIndex,
            index = 1
          ),
          mockedTimestamp,
          "collectingEager",
          variable("static-s-dynamic-0")
        )
      )

      MonitorEmptySink.invocationsCount.get() shouldBe 0
      LogService.invocationsCount.get() shouldBe 0
    }

    "be able to run tests multiple time on the same mini cluster" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName.value)
          .source(sourceNodeId.id, "input")
          .emptySink("out", "valueMonitor", "Value" -> "#input.value1".spel)

      val input = SimpleRecord("0", 11, "2", new Date(3), Some(4), 5, "6")

      val testRunner = prepareTestRunner(useIOMonadInInterpreter)

      def runTestAndVerify() = {
        val results = testRunner
          .runTests(
            process,
            ScenarioTestData(
              List(
                ScenarioTestSourceSpecificFormatJsonRecord(sourceNodeId, Json.fromString("0|11|2|3|4|5|6"))
              )
            )
          )
          .futureValue

        val nodeResults = nodeResultsWithoutTimestamp(results)

        nodeResults(sourceNodeId) shouldBe List(nodeResult(0, "input" -> input))
        nodeResults(NodeId("out")) shouldBe List(nodeResult(0, "input" -> input))

        val expressionEvaluationResults = results.expressionEvaluationResults

        expressionEvaluationResults(NodeId("out")).map(withMockedTimestamp) shouldBe
          List(
            ExpressionEvaluationResult(
              ContextId(
                scenarioName = scenarioName,
                originatingNodeId = sourceNodeId,
                taskId = firstSubtaskIndex,
                index = 0
              ),
              mockedTimestamp,
              "Value",
              variable(11)
            )
          )

        results.externalServiceInvocationResults(NodeId("out")).map(withMockedTimestamp) shouldBe List(
          ExternalServiceInvocationResult(
            ContextId(
              scenarioName = scenarioName,
              originatingNodeId = sourceNodeId,
              taskId = firstSubtaskIndex,
              index = 0
            ),
            mockedTimestamp,
            "valueMonitor",
            variable(11)
          )
        )
      }

      runTestAndVerify()
      runTestAndVerify()
    }

    "collect results for split" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName.value)
          .source(sourceNodeId.id, "input")
          .split("splitId1", GraphBuilder.emptySink("out1", "monitor"), GraphBuilder.emptySink("out2", "monitor"))

      val results = prepareTestRunner(useIOMonadInInterpreter)
        .runTests(
          process,
          ScenarioTestData(List(createTestRecord(), createTestRecord(value1 = 11))),
        )
        .futureValue

      nodeResultsWithoutTimestamp(results)(NodeId("splitId1")) shouldBe List(
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
          .streaming(scenarioName.value)
          .source(sourceNodeId.id, "input")
          .customNode("cid", "out", "stateCustom", "groupBy" -> "#input.id".spel, "stringVal" -> "'s'".spel)
          .emptySink("out", "valueMonitor", "Value" -> "#input.value1 + ' ' + #out.previous".spel)

      val input  = SimpleRecord("0", 1, "2", new Date(3), Some(4), 5, "6")
      val input2 = SimpleRecord("0", 11, "2", new Date(3), Some(4), 5, "6")

      val aggregate  = SimpleRecordWithPreviousValue(input, 0, "s")
      val aggregate2 = SimpleRecordWithPreviousValue(input2, 1, "s")

      val results = prepareTestRunner(useIOMonadInInterpreter)
        .runTests(
          process,
          ScenarioTestData(List(createTestRecord(), createTestRecord(value1 = 11))),
        )
        .futureValue

      val nodeResults = nodeResultsWithoutTimestamp(results)

      nodeResults(sourceNodeId) shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))
      nodeResults(NodeId("cid")) shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))
      nodeResults(NodeId("out")) shouldBe List(
        nodeResult(0, "input" -> input, "out"  -> aggregate),
        nodeResult(1, "input" -> input2, "out" -> aggregate2)
      )

      val expressionEvaluationResults = results.expressionEvaluationResults

      expressionEvaluationResults(NodeId("cid")).map(withMockedTimestamp) shouldBe
        List(
          // we record only LazyParameter execution results
          ExpressionEvaluationResult(
            ContextId(
              scenarioName = scenarioName,
              originatingNodeId = sourceNodeId,
              taskId = firstSubtaskIndex,
              index = 0
            ),
            mockedTimestamp,
            "groupBy",
            variable("0")
          ),
          ExpressionEvaluationResult(
            ContextId(
              scenarioName = scenarioName,
              originatingNodeId = sourceNodeId,
              taskId = firstSubtaskIndex,
              index = 1
            ),
            mockedTimestamp,
            "groupBy",
            variable("0")
          )
        )

      expressionEvaluationResults(NodeId("out")).map(withMockedTimestamp) shouldBe
        List(
          ExpressionEvaluationResult(
            ContextId(
              scenarioName = scenarioName,
              originatingNodeId = sourceNodeId,
              taskId = firstSubtaskIndex,
              index = 0
            ),
            mockedTimestamp,
            "Value",
            variable("1 0")
          ),
          ExpressionEvaluationResult(
            ContextId(
              scenarioName = scenarioName,
              originatingNodeId = sourceNodeId,
              taskId = firstSubtaskIndex,
              index = 1
            ),
            mockedTimestamp,
            "Value",
            variable("11 1")
          )
        )

      results.externalServiceInvocationResults(NodeId("out")).map(withMockedTimestamp) shouldBe
        List(
          ExternalServiceInvocationResult(
            ContextId(
              scenarioName = scenarioName,
              originatingNodeId = sourceNodeId,
              taskId = firstSubtaskIndex,
              index = 0
            ),
            mockedTimestamp,
            "valueMonitor",
            variable("1 0")
          ),
          ExternalServiceInvocationResult(
            ContextId(
              scenarioName = scenarioName,
              originatingNodeId = sourceNodeId,
              taskId = firstSubtaskIndex,
              index = 1
            ),
            mockedTimestamp,
            "valueMonitor",
            variable("11 1")
          )
        )
    }

    "handle large parallelism" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName.value)
          .parallelism(FlinkMiniClusterFactory.DefaultTaskSlots + 1)
          .source(sourceNodeId.id, "input")
          .emptySink("out", "monitor")

      val results =
        prepareTestRunner(useIOMonadInInterpreter)
          .runTests(
            process,
            ScenarioTestData(createTestRecord() :: List.fill(4)(createTestRecord(value1 = 11))),
          )
          .futureValue

      val nodeResults = nodeResultsWithoutTimestamp(results)

      nodeResults(sourceNodeId) should have length 5
    }

    "detect errors" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName.value)
          .source(sourceNodeId.id, "input")
          .processor("failing", "throwingService", "throw" -> "#input.value1 == 2".spel)
          .filter("filter", "1 / #input.value1 >= 0".spel)
          .emptySink("out", "monitor")

      val results = prepareTestRunner(useIOMonadInInterpreter)
        .runTests(
          process,
          ScenarioTestData(
            List(
              createTestRecord(id = "0", value1 = 1),
              createTestRecord(id = "1", value1 = 0),
              createTestRecord(id = "2", value1 = 2),
              createTestRecord(id = "3", value1 = 4)
            )
          ),
        )
        .futureValue

      val nodeResults = nodeResultsWithoutTimestamp(results)

      nodeResults(sourceNodeId) should have length 4
      nodeResults(NodeId("out")) should have length 2

      results.exceptions should have length 2

      val exceptionFromExpression = results.exceptions.head
      exceptionFromExpression.nodeId shouldBe Some(NodeId("filter"))
      exceptionFromExpression.context
        .variables("input")
        .asInstanceOf[Json]
        .hcursor
        .downField("pretty")
        .focus
        .value
        .toString()
        .startsWith("SimpleJsonRecord(1") // it's not nice..
      exceptionFromExpression.throwable.getMessage shouldBe "Expression [1 / #input.value1 >= 0] evaluation failed, message: / by zero"

      val exceptionFromService = results.exceptions.last
      exceptionFromService.nodeId shouldBe Some(NodeId("failing"))
      exceptionFromService.context
        .variables("input")
        .asInstanceOf[Json]
        .hcursor
        .downField("pretty")
        .focus
        .value
        .toString()
        .startsWith("SimpleJsonRecord(2") // it's not nice..
      exceptionFromService.throwable.getMessage shouldBe "Thrown as expected"
    }

    "ignore real exception handler" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName.value)
          .source(sourceNodeId.id, "input")
          .processor("failing", "throwingService", "throw" -> "#input.value1 == 2".spel)
          .filter("filter", "1 / #input.value1 >= 0".spel)
          .emptySink("out", "monitor")

      val exceptionConsumerId = UUID.randomUUID().toString
      val results = prepareTestRunner(
        useIOMonadInInterpreter,
        enrichDefaultConfig = RecordingExceptionConsumerProvider.configWithProvider(_, exceptionConsumerId)
      ).runTests(
        process,
        scenarioTestData = ScenarioTestData(
          List(
            createTestRecord(id = "0", value1 = 1),
            createTestRecord(id = "1", value1 = 0),
            createTestRecord(id = "2", value1 = 2),
            createTestRecord(id = "3", value1 = 4)
          )
        )
      ).futureValue

      val nodeResults = nodeResultsWithoutTimestamp(results)

      nodeResults(sourceNodeId) should have length 4
      nodeResults(NodeId("out")) should have length 2

      results.exceptions should have length 2
      RecordingExceptionConsumer.exceptionsFor(exceptionConsumerId) shouldBe Symbol("empty")
    }

    "handle transient errors" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName.value)
          .source(sourceNodeId.id, "input")
          .processor("failing", "throwingTransientService", "throw" -> "#input.value1 == 2".spel)
          .emptySink("out", "monitor")

      val runner = prepareTestRunner(useIOMonadInInterpreter)
      def runTests = runner
        .runTests(
          process,
          ScenarioTestData(List(createTestRecord(id = "2", value1 = 2)))
        )
        .futureValue

      intercept[TestFailedException](runTests) should matchPattern {
        case e: TestFailedException if e.getCause.isInstanceOf[JobStateCheckError] =>
      }
    }

    "handle json input" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName.value)
          .source(sourceNodeId.id, "jsonInput")
          .emptySink("out", "valueMonitor", "Value" -> "#input".spel)
      val testData = ScenarioTestData(
        List(
          ScenarioTestSourceSpecificFormatJsonRecord(
            sourceNodeId,
            Json.obj(sourceNodeId.id -> Json.fromString("1"), "field" -> Json.fromString("11"))
          ),
          ScenarioTestSourceSpecificFormatJsonRecord(
            sourceNodeId,
            Json.obj(sourceNodeId.id -> Json.fromString("2"), "field" -> Json.fromString("22"))
          ),
          ScenarioTestSourceSpecificFormatJsonRecord(
            sourceNodeId,
            Json.obj(sourceNodeId.id -> Json.fromString("3"), "field" -> Json.fromString("33"))
          ),
        )
      )

      val results =
        prepareTestRunner(useIOMonadInInterpreter).runTests(process, testData).futureValue

      nodeResultsWithoutTimestamp(results)(sourceNodeId) should have size 3
      results.externalServiceInvocationResults(NodeId("out")).map(withMockedTimestamp) shouldBe
        List(
          ExternalServiceInvocationResult(
            ContextId(
              scenarioName = scenarioName,
              originatingNodeId = sourceNodeId,
              taskId = firstSubtaskIndex,
              index = 0
            ),
            mockedTimestamp,
            "valueMonitor",
            variable(SimpleJsonRecord("1", "11"))
          ),
          ExternalServiceInvocationResult(
            ContextId(
              scenarioName = scenarioName,
              originatingNodeId = sourceNodeId,
              taskId = firstSubtaskIndex,
              index = 1
            ),
            mockedTimestamp,
            "valueMonitor",
            variable(SimpleJsonRecord("2", "22"))
          ),
          ExternalServiceInvocationResult(
            ContextId(
              scenarioName = scenarioName,
              originatingNodeId = sourceNodeId,
              taskId = firstSubtaskIndex,
              index = 2
            ),
            mockedTimestamp,
            "valueMonitor",
            variable(SimpleJsonRecord("3", "33"))
          )
        )
    }

    "handle custom variables in source" in {
      val process = ScenarioBuilder
        .streaming(scenarioName.value)
        .source(sourceNodeId.id, "genericSourceWithCustomVariables", "elements" -> "{'abc'}".spel)
        .emptySink("out", "valueMonitor", "Value" -> "#additionalOne + '|' + #additionalTwo".spel)
      val testData =
        ScenarioTestData(List(ScenarioTestSourceSpecificFormatJsonRecord(sourceNodeId, Json.fromString("abc"))))

      val results =
        prepareTestRunner(useIOMonadInInterpreter).runTests(process, testData).futureValue

      nodeResultsWithoutTimestamp(results)(sourceNodeId) should have size 1
      results.externalServiceInvocationResults(NodeId("out")).map(withMockedTimestamp) shouldBe
        List(
          ExternalServiceInvocationResult(
            ContextId(
              scenarioName = scenarioName,
              originatingNodeId = sourceNodeId,
              taskId = firstSubtaskIndex,
              index = 0
            ),
            mockedTimestamp,
            "valueMonitor",
            variable("transformed:abc|3")
          )
        )
    }

    "give meaningful error messages for sink errors" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName.value)
          .source(sourceNodeId.id, "input")
          .emptySink("out", "sinkForInts", "Value" -> "15 / {0, 1}[0]".spel)

      val results =
        prepareTestRunner(useIOMonadInInterpreter)
          .runTests(
            process,
            ScenarioTestData(List(createTestRecord(id = "2", value1 = 2)))
          )
          .futureValue

      results.exceptions should have length 1
      results.exceptions.head.nodeId shouldBe Some(NodeId("out"))
      results.exceptions.head.throwable.getMessage should include("message: / by zero")
    }

    "be able to test process with time windows" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName.value)
          .source(sourceNodeId.id, "input")
          .customNode("cid", "count", "transformWithTime", "seconds" -> "10".spel)
          .emptySink("out", "monitor")

      def recordWithSeconds(duration: FiniteDuration) =
        ScenarioTestSourceSpecificFormatJsonRecord(sourceNodeId, Json.fromString(s"0|0|0|${duration.toMillis}|0|0|0"))

      val results = prepareTestRunner(useIOMonadInInterpreter)
        .runTests(
          process,
          ScenarioTestData(
            List(
              recordWithSeconds(1 second),
              recordWithSeconds(2 second),
              recordWithSeconds(5 second),
              recordWithSeconds(9 second),
              recordWithSeconds(20 second)
            )
          )
        )
        .futureValue

      val nodeResults = results.nodeResults

      nodeResults(NodeId("out")).map(_.variables) shouldBe List(
        Map("count" -> variable(4)),
        Map("count" -> variable(1))
      )

    }

    "be able to test typed map" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName.value)
          .source(
            sourceNodeId.id,
            "typedJsonInput",
            "type" -> """{"field1": "String", "field2": "java.lang.String"}""".spel
          )
          .emptySink("out", "valueMonitor", "Value" -> "#input.field1 + #input.field2".spel)

      val results = prepareTestRunner(useIOMonadInInterpreter)
        .runTests(
          process,
          ScenarioTestData(
            ScenarioTestSourceSpecificFormatJsonRecord(
              sourceNodeId,
              Json.obj("field1" -> Json.fromString("abc"), "field2" -> Json.fromString("def"))
            ) :: Nil
          )
        )
        .futureValue

      results.expressionEvaluationResults(NodeId("out")).map(_.value) shouldBe List(variable("abcdef"))
    }

    "using dependent services" in {
      val countToPass   = 15
      val valueToReturn = 18

      val process = ScenarioBuilder
        .streaming(scenarioName.value)
        .source(sourceNodeId.id, "input")
        .enricher(
          "dependent",
          "parsed",
          "returningDependentTypeService",
          "definition" -> "{'field1', 'field2'}".spel,
          "toFill"     -> "#input.value1.toString()".spel,
          "count"      -> countToPass.toString.spel
        )
        .emptySink("out", "valueMonitor", "Value" -> "#parsed.size + ' ' + #parsed[0].field2".spel)

      val results =
        prepareTestRunner(useIOMonadInInterpreter)
          .runTests(
            process,
            ScenarioTestData(List(createTestRecord(value1 = valueToReturn)))
          )
          .futureValue

      results.expressionEvaluationResults(NodeId("out")).map(_.value) shouldBe List(
        variable(s"$countToPass $valueToReturn")
      )
    }

    "switch value should be equal to variable value" in {
      val process = ScenarioBuilder
        .streaming(scenarioName.value)
        .parallelism(1)
        .source(sourceNodeId.id, "input")
        .switch(
          "switch",
          "#input.id == 'ala'".spel,
          "output",
          Case(
            "#output == false".spel,
            GraphBuilder.emptySink("out", "valueMonitor", "Value" -> "'any'".spel)
          )
        )

      val recordTrue  = createTestRecord(id = "ala")
      val recordFalse = createTestRecord(id = "bela")

      val results =
        prepareTestRunner(useIOMonadInInterpreter)
          .runTests(process, ScenarioTestData(List(recordTrue, recordFalse)))
          .futureValue

      val expressionEvaluationResults = results.expressionEvaluationResults

      expressionEvaluationResults(NodeId("switch")).filter(_.name == "expression").head.value shouldBe variable(true)
      expressionEvaluationResults(NodeId("switch")).filter(_.name == "expression").last.value shouldBe variable(false)
      // first record was filtered out
      expressionEvaluationResults(NodeId("out")).head.contextId shouldBe ContextId(
        scenarioName = scenarioName,
        originatingNodeId = sourceNodeId,
        taskId = firstSubtaskIndex,
        index = 1,
      )
    }

    "should handle joins for one input (diamond-like) " in {
      val process = ScenarioBuilder
        .streaming(scenarioName.value)
        .sources(
          GraphBuilder
            .source(sourceNodeId.id, "input")
            .split(
              "split",
              GraphBuilder.filter("left", "#input.id != 'a'".spel).branchEnd("end1", "join1"),
              GraphBuilder.filter("right", "#input.id != 'b'".spel).branchEnd("end2", "join1")
            ),
          GraphBuilder
            .join(
              "join1",
              "joinBranchExpression",
              Some("input33"),
              List(
                "end1" -> List("value" -> "#input".spel),
                "end2" -> List("value" -> "#input".spel)
              )
            )
            .processorEnd("proc2", "logService", "all" -> "#input33.id".spel)
        )

      val recA = createTestRecord(id = "a")
      val recB = createTestRecord(id = "b")
      val recC = createTestRecord(id = "c")

      val results =
        prepareTestRunner(useIOMonadInInterpreter)
          .runTests(process, ScenarioTestData(List(recA, recB, recC)))
          .futureValue

      results.expressionEvaluationResults(NodeId("proc2")).map(_.contextId) should contain only (
        ContextId(
          scenarioName = scenarioName,
          originatingNodeId = sourceNodeId,
          taskId = firstSubtaskIndex,
          index = 1,
          path = List(
            ContextIdPathPart(NodeId("split"), "left"),
            ContextIdPathPart(NodeId("join1"), "end1"),
          )
        ),
        ContextId(
          scenarioName = scenarioName,
          originatingNodeId = sourceNodeId,
          taskId = firstSubtaskIndex,
          index = 2,
          path = List(
            ContextIdPathPart(NodeId("split"), "left"),
            ContextIdPathPart(NodeId("join1"), "end1"),
          )
        ),
        ContextId(
          scenarioName = scenarioName,
          originatingNodeId = sourceNodeId,
          taskId = firstSubtaskIndex,
          index = 0,
          path = List(
            ContextIdPathPart(NodeId("split"), "right"),
            ContextIdPathPart(NodeId("join1"), "end2"),
          )
        ),
        ContextId(
          scenarioName = scenarioName,
          originatingNodeId = sourceNodeId,
          taskId = firstSubtaskIndex,
          index = 2,
          path = List(
            ContextIdPathPart(NodeId("split"), "right"),
            ContextIdPathPart(NodeId("join1"), "end2"),
          )
        ),
      )

      results
        .externalServiceInvocationResults(NodeId("proc2"))
        .map(_.value.asInstanceOf[Json]) should contain theSameElementsAs List(
        "b",
        "a",
        "c",
        "c"
      ).map(_ + "-collectedDuringServiceInvocation").map(variable)
    }

    "should test multiple source scenario" in {
      val process = ScenarioBuilder
        .streaming(scenarioName.value)
        .sources(
          GraphBuilder
            .source("source1", "input")
            .filter("filter1", "#input.id != 'a'".spel)
            .branchEnd("end1", "join"),
          GraphBuilder
            .source("source2", "input")
            .filter("filter2", "#input.id != 'b'".spel)
            .branchEnd("end2", "join"),
          GraphBuilder
            .join(
              "join",
              "joinBranchExpression",
              Some("joinInput"),
              List(
                "end1" -> List("value" -> "#input".spel),
                "end2" -> List("value" -> "#input".spel)
              )
            )
            .processorEnd("proc2", "logService", "all" -> "#joinInput.id".spel)
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

      val results = prepareTestRunner(useIOMonadInInterpreter)
        .runTests(process, scenarioTestData)
        .futureValue

      val nodeResults = nodeResultsWithoutTimestamp(results)
      nodeResults(NodeId("source1")) shouldBe List(
        nodeResult(0, NodeId("source1"), "input" -> recordA),
        nodeResult(1, NodeId("source1"), "input" -> recordD)
      )
      nodeResults(NodeId("source2")) shouldBe List(
        nodeResult(0, NodeId("source2"), "input" -> recordA),
        nodeResult(1, NodeId("source2"), "input" -> recordB),
        nodeResult(2, NodeId("source2"), "input" -> recordC)
      )
      nodeResults(NodeId("filter1")) shouldBe nodeResults(NodeId("source1"))
      nodeResults(NodeId("filter2")) shouldBe nodeResults(NodeId("source2"))
      nodeResults(NodeId("$edge-end1-join")) shouldBe List(nodeResult(1, NodeId("source1"), "input" -> recordD))
      nodeResults(NodeId("$edge-end2-join")) shouldBe List(
        nodeResult(0, NodeId("source2"), "input" -> recordA),
        nodeResult(2, NodeId("source2"), "input" -> recordC)
      )
      nodeResults(NodeId("join")) should contain only (
        nodeResult(1, NodeId("source1"), "end1", "input" -> recordD, "joinInput" -> recordD),
        nodeResult(0, NodeId("source2"), "end2", "input" -> recordA, "joinInput" -> recordA),
        nodeResult(2, NodeId("source2"), "end2", "input" -> recordC, "joinInput" -> recordC)
      )

      results.expressionEvaluationResults(NodeId("proc2")).map(withMockedTimestamp) should contain only (
        ExpressionEvaluationResult(
          ContextId(
            scenarioName = scenarioName,
            originatingNodeId = NodeId("source1"),
            taskId = firstSubtaskIndex,
            index = 1,
            path = List(ContextIdPathPart(NodeId("join"), "end1"))
          ),
          mockedTimestamp,
          "all",
          variable("d")
        ),
        ExpressionEvaluationResult(
          ContextId(
            scenarioName = scenarioName,
            originatingNodeId = NodeId("source2"),
            taskId = firstSubtaskIndex,
            index = 0,
            path = List(ContextIdPathPart(NodeId("join"), "end2"))
          ),
          mockedTimestamp,
          "all",
          variable("a")
        ),
        ExpressionEvaluationResult(
          ContextId(
            scenarioName = scenarioName,
            originatingNodeId = NodeId("source2"),
            taskId = firstSubtaskIndex,
            index = 2,
            path = List(ContextIdPathPart(NodeId("join"), "end2"))
          ),
          mockedTimestamp,
          "all",
          variable("c")
        )
      )

      results
        .externalServiceInvocationResults(NodeId("proc2"))
        .map(_.value.asInstanceOf[Json]) should contain theSameElementsAs List("a", "c", "d")
        .map(_ + "-collectedDuringServiceInvocation")
        .map(variable)
    }

    "should have correct run mode" in {
      val process = ScenarioBuilder
        .streaming(scenarioName.value)
        .source("start", "input")
        .enricher("componentUseContextService", "componentUseContextService", "returningComponentUseContextService")
        .customNode(
          "componentUseContextCustomNode",
          "componentUseContextCustomNode",
          "transformerAddingComponentUseContext"
        )
        .emptySink(
          "out",
          "valueMonitor",
          "Value" -> "{#componentUseContextService, #componentUseContextCustomNode}".spel
        )

      val results =
        prepareTestRunner(useIOMonadInInterpreter)
          .runTests(
            process,
            ScenarioTestData(List(createTestRecord(sourceId = "start")))
          )
          .futureValue

      results.expressionEvaluationResults(NodeId("out")).map(_.value) shouldBe List(
        variable(List(ComponentUseContext.ScenarioTesting, ComponentUseContext.ScenarioTesting))
      )
    }

    "should throw exception when parameter was modified by AdditionalUiConfigProvider with dict editor and flink wasn't provided with additional config" in {
      val process =
        ScenarioBuilder
          .streaming(scenarioName.value)
          .source(sourceNodeId.id, "input")
          .processor(
            "eager1",
            "collectingEager",
            "static"  -> Expression.dictKeyWithLabel("'s'", Some("s")),
            "dynamic" -> "#input.id".spel
          )
          .emptySink("out", "valueMonitor", "Value" -> "#input.value1".spel)

      intercept[TestFailedException] {
        prepareTestRunner(useIOMonadInInterpreter)
          .runTests(
            process,
            ScenarioTestData(List(createTestRecord(id = "2", value1 = 2)))
          )
          .futureValue
      }.getCause should matchPattern {
        case ScenarioCompilationErrors(
              IncompatibleParameterDefinitionModification(
                ParameterName("static"),
                `DictKeyWithLabel`,
                List(SpelParameterEditor, SpelTemplateParameterEditor),
                NodeId("eager1")
              ) :: Nil
            ) =>
      }
    }

    "should run correctly when parameter was modified by AdditionalUiConfigProvider with dict editor and flink was provided with additional config" in {
      val modifiedComponentName = "collectingEager"
      val modifiedParameterName = "static"
      val process =
        ScenarioBuilder
          .streaming(scenarioName.value)
          .source(sourceNodeId.id, "input")
          .processor(
            "eager1",
            modifiedComponentName,
            modifiedParameterName -> Expression.dictKeyWithLabel("'s'", Some("s")),
            "dynamic"             -> "#input.id".spel
          )
          .emptySink("out", "valueMonitor", "Value" -> "#input.value1".spel)

      val results = prepareTestRunner(
        useIOMonadInInterpreter,
        additionalConfigsFromProvider = Map(
          DesignerWideComponentId("service-" + modifiedComponentName) -> ComponentAdditionalConfig(
            parameterConfigs = Map(
              ParameterName(modifiedParameterName) -> ParameterAdditionalUIConfig(
                required = false,
                initialValue = None,
                hintText = None,
                valueEditor = Some(ValueInputWithDictEditor("someDictId", allowOtherValue = false)),
                valueCompileTimeValidation = None
              )
            )
          )
        )
      ).runTests(process, ScenarioTestData(List(createTestRecord(id = "2", value1 = 2)))).futureValue
      results.exceptions should have length 0
    }

    "should not throw exception when process fragment has parameter validation defined" in {
      val scenario = ScenarioBuilder
        .streaming("scenario1")
        .source(sourceNodeId.id, "input")
        .fragmentOneOut("sub", fragmentWithValidationName, "output", "fragmentResult", "param" -> "'asd'".spel)
        .emptySink("out", "valueMonitor", "Value" -> "1".spel)

      val resolved = FragmentResolver(List(processWithFragmentParameterValidation)).resolve(scenario)

      val results = prepareTestRunner(useIOMonadInInterpreter)
        .runTests(
          resolved.valueOr { _ => throw new IllegalArgumentException("Won't happen") },
          ScenarioTestData(
            List(ScenarioTestSourceSpecificFormatJsonRecord(sourceNodeId, Json.fromString("0|1|2|3|4|5|6")))
          ),
        )
        .futureValue
      results.exceptions.length shouldBe 0
    }
  }

  private def createTestRecord(
      sourceId: String = sourceNodeId.id,
      id: String = "0",
      value1: Long = 1
  ): ScenarioTestSourceSpecificFormatJsonRecord =
    ScenarioTestSourceSpecificFormatJsonRecord(NodeId(sourceId), Json.fromString(s"$id|$value1|2|3|4|5|6"))

  private def prepareTestRunner(
      useIOMonadInInterpreter: Boolean,
      enrichDefaultConfig: Config => Config = identity,
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig] = Map.empty,
      waitForJobIsFinishedRetryPolicy: retry.Policy =
        DurationToRetryPolicyConverter.toPausePolicy(patienceConfig.timeout - 3.second, patienceConfig.interval * 2)
  ): FlinkMiniClusterScenarioTestRunner = {
    val config = enrichDefaultConfig(ConfigFactory.load("application.conf"))
      .withValue("globalParameters.useIOMonadInInterpreter", ConfigValueFactory.fromAnyRef(useIOMonadInInterpreter))

    val modelData = LocalModelData(
      config,
      List.empty,
      configCreator = new SimpleProcessConfigCreator,
      additionalConfigsFromProvider = additionalConfigsFromProvider,
      modelClassLoader = ModelClassLoader.flinkWorkAroundEmptyClassloader,
    )
    new FlinkMiniClusterScenarioTestRunner(
      modelData.toModelDataProvider,
      miniClusterWithServices,
      parallelism = 1,
      waitForJobIsFinishedRetryPolicy
    )
  }

  private def nodeResult(count: Int, vars: (String, Any)*): (ContextId, Map[String, Json]) =
    nodeResult(count, sourceNodeId, vars: _*)

  private def nodeResult(count: Int, sourceId: NodeId, vars: (String, Any)*): (ContextId, Map[String, Json]) =
    (
      ContextId(scenarioName, sourceId, firstSubtaskIndex, count),
      Map(vars: _*).mapValuesNow(variable)
    )

  private def nodeResult(
      count: Int,
      sourceId: NodeId,
      branchId: String,
      vars: (String, Any)*
  ): (ContextId, Map[String, Json]) =
    (
      ContextId(
        scenarioName,
        sourceId,
        firstSubtaskIndex,
        count,
        List(ContextIdPathPart(NodeId("join"), branchId)),
      ),
      Map(vars: _*).mapValuesNow(variable)
    )

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

  private def nodeResultsWithoutTimestamp[T](results: TestProcess.TestResults[T]) =
    results.nodeResults.map { case (key, values) => (key, values.map(v => (v.id, v.variables))) }

  private def withMockedTimestamp(result: ExpressionEvaluationResult[Json]) =
    result.copy(timestamp = mockedTimestamp)

  private def withMockedTimestamp(result: ExternalServiceInvocationResult[Json]) =
    result.copy(timestamp = mockedTimestamp)

}

object FlinkMiniClusterScenarioTestRunnerSpec {
  private val fragmentWithValidationName = "fragmentWithValidation"

  private val processWithFragmentParameterValidation: CanonicalProcess = {
    val fragmentParamName = ParameterName("param")
    val fragmentParam = FragmentParameter(fragmentParamName, FragmentClazzRef[String]).copy(
      valueCompileTimeValidation = Some(
        ParameterValueCompileTimeValidation(
          validationExpression = Expression.spel("true"),
          validationFailedMessage = Some("param validation failed"),
          None
        )
      )
    )

    CanonicalProcess(
      MetaData(fragmentWithValidationName, FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition("start", List(fragmentParam))
        ),
        FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      List.empty
    )
  }

}
