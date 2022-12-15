package pl.touk.nussknacker.engine.process.runner

import com.typesafe.config.{Config, ConfigFactory}
import io.circe.{Json, parser}
import org.apache.flink.runtime.client.JobExecutionException
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, Inside}
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, RecordingExceptionConsumer, RecordingExceptionConsumerProvider}
import pl.touk.nussknacker.engine.graph.node.Case
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.engine.{ModelData, spel}
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

import java.nio.charset.StandardCharsets
import java.util.{Date, UUID}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class FlinkTestMainSpec extends AnyFunSuite with Matchers with Inside with BeforeAndAfterEach with EitherValuesDetailedMessage {

  import spel.Implicits._

  import scala.collection.JavaConverters._

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    MonitorEmptySink.clear()
    LogService.clear()
  }

  test("be able to return test results") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .filter("filter1", "#input.value1 > 1")
        .buildSimpleVariable("v1", "variable1", "'ala'")
        .processor("eager1", "collectingEager", "static" -> "'s'", "dynamic" -> "#input.id")
        .processor("proc2", "logService", "all" -> "#input.id")
        .emptySink("out", "valueMonitor", "value" -> "#input.value1")

    val input = SimpleRecord("0", 1, "2", new Date(3), Some(4), 5, "6")
    val input2 = SimpleRecord("0", 11, "2", new Date(3), Some(4), 5, "6")

    val results = runFlinkTest(process, TestData.asJsonStrings("0|1|2|3|4|5|6", "0|11|2|3|4|5|6"))

    val nodeResults = results.nodeResults

    nodeResults("id") shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))
    nodeResults("filter1") shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))
    nodeResults("v1") shouldBe List(nodeResult(1, "input" -> input2))
    nodeResults("proc2") shouldBe List(nodeResult(1, "input" -> input2, "variable1" -> "ala"))
    nodeResults("out") shouldBe List(nodeResult(1, "input" -> input2, "variable1" -> "ala"))

    val invocationResults = results.invocationResults

    invocationResults("proc2") shouldBe
      List(ExpressionInvocationResult("proc1-id-0-1", "all", "0"))

    invocationResults("out") shouldBe
      List(ExpressionInvocationResult("proc1-id-0-1", "value", 11))

    results.externalInvocationResults("proc2") shouldBe List(ExternalInvocationResult("proc1-id-0-1", "logService", "0-collectedDuringServiceInvocation"))
    results.externalInvocationResults("out") shouldBe List(ExternalInvocationResult("proc1-id-0-1", "valueMonitor", 11))
    results.externalInvocationResults("eager1") shouldBe List(ExternalInvocationResult("proc1-id-0-1", "collectingEager", "static-s-dynamic-0"))

    MonitorEmptySink.invocationsCount.get() shouldBe 0
    LogService.invocationsCount.get() shouldBe 0
  }

  test("collect results for split") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .split("splitId1",
          GraphBuilder.emptySink("out1", "monitor"),
          GraphBuilder.emptySink("out2", "monitor")
        )

    val results = runFlinkTest(process, TestData.asJsonStrings("0|1|2|3|4|5|6", "0|11|2|3|4|5|6"))

    results.nodeResults("splitId1") shouldBe List(nodeResult(0, "input" ->
        SimpleRecord("0", 1, "2", new Date(3), Some(4), 5, "6")),
      nodeResult(1, "input" ->
        SimpleRecord("0", 11, "2", new Date(3), Some(4), 5, "6")))
  }

  test("return correct result for custom node") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .customNode("cid", "out", "stateCustom", "groupBy" -> "#input.id", "stringVal" -> "'s'")
        .emptySink("out", "valueMonitor", "value" -> "#input.value1 + ' ' + #out.previous")

    val input = SimpleRecord("0", 1, "2", new Date(3), Some(4), 5, "6")
    val input2 = SimpleRecord("0", 11, "2", new Date(3), Some(4), 5, "6")

    val aggregate = SimpleRecordWithPreviousValue(input, 0, "s")
    val aggregate2 = SimpleRecordWithPreviousValue(input2, 1, "s")


    val results = runFlinkTest(process, TestData.asJsonStrings("0|1|2|3|4|5|6", "0|11|2|3|4|5|6"))

    val nodeResults = results.nodeResults

    nodeResults("id") shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))
    nodeResults("cid") shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))
    nodeResults("out") shouldBe List(
      nodeResult(0, "input" -> input, "out" -> aggregate),
      nodeResult(1, "input" -> input2, "out" -> aggregate2))

    val invocationResults = results.invocationResults

    invocationResults("cid") shouldBe
      List(
        //we record only LazyParameter execution results
        ExpressionInvocationResult("proc1-id-0-0", "groupBy", "0"),
        ExpressionInvocationResult("proc1-id-0-1", "groupBy", "0")
      )

    invocationResults("out") shouldBe
      List(
        ExpressionInvocationResult("proc1-id-0-0", "value", "1 0"),
        ExpressionInvocationResult("proc1-id-0-1", "value", "11 1")
      )

    results.externalInvocationResults("out") shouldBe
      List(
        ExternalInvocationResult("proc1-id-0-0", "valueMonitor", "1 0"),
        ExternalInvocationResult("proc1-id-0-1", "valueMonitor", "11 1")
      )
  }

  test("handle large parallelism") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .parallelism(4)
        .source("id", "input")
        .emptySink("out", "monitor")

    val results = runFlinkTest(process, TestData.asJsonStrings("0|1|2|3|4|5|6", "0|11|2|3|4|5|6", "0|11|2|3|4|5|6", "0|11|2|3|4|5|6", "0|11|2|3|4|5|6"))

    val nodeResults = results.nodeResults

    nodeResults("id") should have length 5

  }

  test("detect errors") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .processor("failing", "throwingService", "throw" -> "#input.value1 == 2")
        .filter("filter", "1 / #input.value1 >= 0")
        .emptySink("out", "monitor")

    val results = runFlinkTest(process, TestData.asJsonStrings("0|1|2|3|4|5|6", "1|0|2|3|4|5|6", "2|2|2|3|4|5|6", "3|4|2|3|4|5|6"))

    val nodeResults = results.nodeResults

    nodeResults("id") should have length 4
    nodeResults("out") should have length 2

    results.exceptions should have length 2

    val exceptionFromExpression = results.exceptions.head
    exceptionFromExpression.nodeId shouldBe Some("filter")
    exceptionFromExpression.context.variables("input").asInstanceOf[SimpleRecord].id shouldBe "1"
    exceptionFromExpression.throwable.getMessage shouldBe "Expression [1 / #input.value1 >= 0] evaluation failed, message: / by zero"

    val exceptionFromService = results.exceptions.last
    exceptionFromService.nodeId shouldBe Some("failing")
    exceptionFromService.context.variables("input").asInstanceOf[SimpleRecord].id shouldBe "2"
    exceptionFromService.throwable.getMessage shouldBe "Thrown as expected"
  }

  test("ignore real exception handler") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .processor("failing", "throwingService", "throw" -> "#input.value1 == 2")
        .filter("filter", "1 / #input.value1 >= 0")
        .emptySink("out", "monitor")

    val exceptionConsumerId = UUID.randomUUID().toString
    val config = RecordingExceptionConsumerProvider.configWithProvider(ConfigFactory.load(), exceptionConsumerId)
    val results = runFlinkTest(process, TestData.asJsonStrings("0|1|2|3|4|5|6", "1|0|2|3|4|5|6", "2|2|2|3|4|5|6", "3|4|2|3|4|5|6"), config)

    val nodeResults = results.nodeResults

    nodeResults("id") should have length 4
    nodeResults("out") should have length 2

    results.exceptions should have length 2
    RecordingExceptionConsumer.dataFor(exceptionConsumerId) shouldBe 'empty
  }

  test("handle transient errors") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .processor("failing", "throwingTransientService", "throw" -> "#input.value1 == 2")
        .emptySink("out", "monitor")

    val run = Future {
      runFlinkTest(process, TestData.asJsonStrings("2|2|2|3|4|5|6"))
    }

    intercept[JobExecutionException](Await.result(run, 10 seconds))
  }

  test("handle json input") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "jsonInput")
        .emptySink("out", "valueMonitor", "value" -> "#input")
    val testData = TestData(List(
      TestRecord(parser.parse("""{"id": "1", "field": "11"}""").right.get),
      TestRecord(parser.parse("""{"id": "2", "field": "22"}""").right.get),
      TestRecord(parser.parse("""{"id": "3", "field": "33"}""").right.get),
    ))

    val results = runFlinkTest(process, testData)

    results.nodeResults("id") should have size 3
    results.externalInvocationResults("out") shouldBe
      List(
        ExternalInvocationResult("proc1-id-0-0", "valueMonitor", SimpleJsonRecord("1", "11")),
        ExternalInvocationResult("proc1-id-0-1", "valueMonitor", SimpleJsonRecord("2", "22")),
        ExternalInvocationResult("proc1-id-0-2", "valueMonitor", SimpleJsonRecord("3", "33"))
      )
  }

  test("handle custom variables in source") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "genericSourceWithCustomVariables", "elements" -> "{'abc'}")
      .emptySink("out", "valueMonitor", "value" -> "#additionalOne + '|' + #additionalTwo")
    val testData = TestData.asJsonStrings("abc")

    val results = runFlinkTest(process, testData)

    results.nodeResults("id") should have size 1
    results.externalInvocationResults("out") shouldBe
      List(
        ExternalInvocationResult("proc1-id-0-0", "valueMonitor", "transformed:abc|3")
      )
  }

  test("give meaningful error messages for sink errors") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .emptySink("out", "sinkForInts", "value" -> "15 / {0, 1}[0]")

    val results = runFlinkTest(process, TestData.asJsonStrings("2|2|2|3|4|5|6"))

    results.exceptions should have length 1
    results.exceptions.head.nodeId shouldBe Some("out")
    results.exceptions.head.throwable.getMessage should include ("message: / by zero")

    SinkForInts.data should have length 0
  }

  test("be able to test process with time windows") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .customNode("cid", "count", "transformWithTime", "seconds" -> "10")
        .emptySink("out", "monitor")

    def recordWithSeconds(duration: FiniteDuration) = s"0|0|0|${duration.toMillis}|0|0|0"

    val results = runFlinkTest(process, TestData.asJsonStrings(
      recordWithSeconds(1 second),
      recordWithSeconds(2 second),
      recordWithSeconds(5 second),
      recordWithSeconds(9 second),
      recordWithSeconds(20 second)
    ))

    val nodeResults = results.nodeResults

    nodeResults("out").map(_.context.variables) shouldBe List(Map("count" -> 4), Map("count" -> 1))

  }

  test("be able to test typed map") {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "typedJsonInput", "type" -> """{"field1": "String", "field2": "java.lang.String"}""")
        .emptySink("out", "valueMonitor", "value" -> "#input.field1 + #input.field2")

    val results = runFlinkTest(process, TestData(TestRecord(parser.parse("""{"field1": "abc", "field2": "def"}""").rightValue) :: Nil))

    results.invocationResults("out").map(_.value) shouldBe List("abcdef")
  }

  test("using dependent services") {
    val countToPass = "15"
    val valueToReturn = "18"

    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .enricher("dependent", "parsed", "returningDependentTypeService",
        "definition" -> "{'field1', 'field2'}", "toFill" -> "#input.value1.toString()", "count" -> countToPass)
      .emptySink("out", "valueMonitor", "value" ->  "#parsed.size + ' ' + #parsed[0].field2")

    val results = runFlinkTest(process, TestData.asJsonStrings(s"0|$valueToReturn|2|3|4|5|6"))

    results.invocationResults("out").map(_.value) shouldBe List(s"$countToPass $valueToReturn")
  }

  test("switch value should be equal to variable value") {
    import spel.Implicits._

    val process = ScenarioBuilder
      .streaming("sampleProcess")
      .parallelism(1)
      .source("id", "input")
      .switch("switch", "#input.id == 'ala'", "output",
        Case(
          "#output == false",
          GraphBuilder.emptySink("out", "valueMonitor", "value" -> "'any'")
        )
      )

    val recordTrue = "ala|1|2|3|4|5|6"
    val recordFalse = "bela|1|2|3|4|5|6"

    val results = runFlinkTest(process, TestData.asJsonStrings(recordTrue, recordFalse))

    val invocationResults = results.invocationResults

    invocationResults("switch").filter(_.name == "expression").head.value shouldBe true
    invocationResults("switch").filter(_.name == "expression").last.value shouldBe false
    // first record was filtered out
    invocationResults("out").head.contextId shouldBe "sampleProcess-id-0-1"
  }

  //TODO: in the future we should also handle multiple sources tests...
  test("should handle joins for one input (diamond-like) ") {
    val process = ScenarioBuilder.streaming("proc1").sources(
      GraphBuilder.source("id", "input")
        .split("split",
          GraphBuilder.filter("left", "#input.id != 'a'").branchEnd("end1", "join1"),
          GraphBuilder.filter("right", "#input.id != 'b'").branchEnd("end2", "join1")
        ),
      GraphBuilder.join("join1", "joinBranchExpression", Some("input33"),
        List(
          "end1" -> List("value" -> "#input"),
          "end2" -> List("value" -> "#input")
        ))
        .processorEnd("proc2", "logService", "all" -> "#input33.id")
    )

    val recA = "a|1|2|1|4|5|6"
    val recB = "b|1|2|2|4|5|6"
    val recC = "c|1|2|3|4|5|6"


    val results = runFlinkTest(process, TestData.asJsonStrings(recA, recB, recC))

    //TODO: currently e.g. invocation results will behave strangely in this test, because we duplicate inputs and this results in duplicate context ids...
    results.externalInvocationResults("proc2").map(_.value.asInstanceOf[String]).sorted shouldBe List("a", "b", "c", "c").map(_ + "-collectedDuringServiceInvocation")
  }

  test("should have correct run mode") {
    val process = ScenarioBuilder
      .streaming("proc")
      .source("start", "input")
      .enricher("componentUseCaseService", "componentUseCaseService", "returningComponentUseCaseService")
      .customNode("componentUseCaseCustomNode", "componentUseCaseCustomNode", "transformerAddingComponentUseCase")
      .emptySink("out", "valueMonitor", "value" -> "{#componentUseCaseService, #componentUseCaseCustomNode}")

    val results = runFlinkTest(process, TestData.asJsonStrings("0|1|2|3|4|5|6"))

    results.invocationResults("out").map(_.value) shouldBe List(List(ComponentUseCase.TestRuntime, ComponentUseCase.TestRuntime).asJava)
  }

  def runFlinkTest(process: CanonicalProcess, testData: TestData, config: Config= ConfigFactory.load()): TestResults[Any] = {
    //We need to set context loader to avoid forking in sbt
    val modelData = ModelData(config, ModelClassLoader.empty)
    ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
      FlinkTestMain.run(modelData, process, testData, FlinkTestConfiguration.configuration(), identity)
    }
  }

  def nodeResult(count: Int, vars: (String, Any)*): NodeResult[Any] =
    NodeResult(ResultContext[Any](s"proc1-id-0-$count", Map(vars: _*)))
}

