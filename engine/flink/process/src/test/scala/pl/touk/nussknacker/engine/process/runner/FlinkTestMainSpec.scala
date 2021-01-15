package pl.touk.nussknacker.engine.process.runner

import java.util.Date

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.apache.flink.runtime.client.JobExecutionException
import org.scalatest._
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.deployment.TestProcess._
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.Case
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.engine.{ClassLoaderModelData, ModelConfigToLoad, spel}
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class FlinkTestMainSpec extends FunSuite with Matchers with Inside with BeforeAndAfterEach {

  import spel.Implicits._

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    MonitorEmptySink.clear()
    LogService.clear()
    RecordingExceptionHandler.clear()
  }

  private val modelData = ClassLoaderModelData(ConfigFactory.load(), ModelClassLoader.empty)

  private def marshall(process: EspProcess): String = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2

  test("be able to return test results") {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .filter("filter1", "#input.value1 > 1")
        .buildSimpleVariable("v1", "variable1", "'ala'")
        .processor("proc2", "logService", "all" -> "#input.id")
        .sink("out", "#input.value1", "monitor")

    val input = SimpleRecord("0", 1, "2", new Date(3), Some(4), 5, "6")
    val input2 = SimpleRecord("0", 11, "2", new Date(3), Some(4), 5, "6")

    val results = FlinkTestMain.run(modelData, marshall(process), TestData(List("0|1|2|3|4|5|6", "0|11|2|3|4|5|6").mkString("\n")), FlinkTestConfiguration.configuration(), identity)

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
      List(ExpressionInvocationResult("proc1-id-0-1", "expression", 11))

    results.mockedResults("proc2") shouldBe List(MockedResult("proc1-id-0-1", "logService", "0-collectedDuringServiceInvocation"))
    results.mockedResults("out") shouldBe List(MockedResult("proc1-id-0-1", "monitor", "11"))
    MonitorEmptySink.invocationsCount.get() shouldBe 0
    LogService.invocationsCount.get() shouldBe 0
  }

  test("collect results for split") {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .split("splitId1",
          GraphBuilder.sink("out1", "'123'", "monitor"),
          GraphBuilder.sink("out2", "'234'", "monitor")
        )

    val results = FlinkTestMain.run(modelData, marshall(process), TestData(List("0|1|2|3|4|5|6", "0|11|2|3|4|5|6").mkString("\n")), FlinkTestConfiguration.configuration(), identity)

    results.nodeResults("splitId1") shouldBe List(nodeResult(0, "input" ->
        SimpleRecord("0", 1, "2", new Date(3), Some(4), 5, "6")),
      nodeResult(1, "input" ->
        SimpleRecord("0", 11, "2", new Date(3), Some(4), 5, "6")))
  }

  test("return correct result for custom node") {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .customNode("cid", "out", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "'s'")
        .sink("out", "#input.value1 + ' ' + #out.previous", "monitor")

    val input = SimpleRecord("0", 1, "2", new Date(3), Some(4), 5, "6")
    val input2 = SimpleRecord("0", 11, "2", new Date(3), Some(4), 5, "6")

    val aggregate = SimpleRecordWithPreviousValue(input, 0, "s")
    val aggregate2 = SimpleRecordWithPreviousValue(input2, 1, "s")


    val results = FlinkTestMain.run(modelData, marshall(process), TestData(List("0|1|2|3|4|5|6", "0|11|2|3|4|5|6").mkString("\n")), FlinkTestConfiguration.configuration(), identity)

    val nodeResults = results.nodeResults

    nodeResults("id") shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))

    val resultsAfterCid = List(
      nodeResult(0, "input" -> input, "out" -> aggregate),
      nodeResult(1, "input" -> input2, "out" -> aggregate2))

    nodeResults("cid") shouldBe resultsAfterCid
    nodeResults("out") shouldBe resultsAfterCid

    val invocationResults = results.invocationResults

    invocationResults("cid") shouldBe
      List(
        //we record only LazyParameter execution results
        ExpressionInvocationResult("proc1-id-0-0", "keyBy", "0"),
        ExpressionInvocationResult("proc1-id-0-1", "keyBy", "0")
      )
    invocationResults("out") shouldBe
      List(
        ExpressionInvocationResult("proc1-id-0-0", "expression", "1 0"),
        ExpressionInvocationResult("proc1-id-0-1", "expression", "11 1")
      )

    results.mockedResults("out") shouldBe
      List(
        MockedResult("proc1-id-0-0", "monitor", "1 0"),
        MockedResult("proc1-id-0-1", "monitor", "11 1")
      )

  }

  test("handle large parallelism") {
    val process =
      EspProcessBuilder
        .id("proc1")
        .parallelism(4)
        .exceptionHandler()
        .source("id", "input")
        .sink("out", "#input", "monitor")

    val results = FlinkTestMain.run(modelData, marshall(process), TestData(List("0|1|2|3|4|5|6", "0|11|2|3|4|5|6", "0|11|2|3|4|5|6", "0|11|2|3|4|5|6", "0|11|2|3|4|5|6").mkString("\n")), FlinkTestConfiguration.configuration(), identity)

    val nodeResults = results.nodeResults

    nodeResults("id") should have length 5

  }

  test("detect errors") {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .processor("failing", "throwingService", "throw" -> "#input.value1 == 2")
        .filter("filter", "1 / #input.value1 >= 0")
        .sink("out", "#input", "monitor")

    val results = FlinkTestMain.run(modelData, marshall(process), TestData(List("0|1|2|3|4|5|6", "1|0|2|3|4|5|6", "2|2|2|3|4|5|6", "3|4|2|3|4|5|6").mkString("\n")), FlinkTestConfiguration.configuration(), identity)

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
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .processor("failing", "throwingService", "throw" -> "#input.value1 == 2")
        .filter("filter", "1 / #input.value1 >= 0")
        .sink("out", "#input", "monitor")

    val results = FlinkTestMain.run(modelData, marshall(process), TestData(List("0|1|2|3|4|5|6", "1|0|2|3|4|5|6", "2|2|2|3|4|5|6", "3|4|2|3|4|5|6").mkString("\n")), FlinkTestConfiguration.configuration(), identity)

    val nodeResults = results.nodeResults

    nodeResults("id") should have length 4
    nodeResults("out") should have length 2

    results.exceptions should have length 2
    RecordingExceptionHandler.data shouldBe 'empty
  }

  test("handle transient errors") {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .processor("failing", "throwingTransientService", "throw" -> "#input.value1 == 2")
        .sink("out", "#input", "monitor")

    val run = Future {
      FlinkTestMain.run(modelData, marshall(process), TestData(List("2|2|2|3|4|5|6").mkString("\n")), FlinkTestConfiguration.configuration(), identity)
    }

    intercept[JobExecutionException](Await.result(run, 10 seconds))


  }

  test("handle custom multiline source input") {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "jsonInput")
        .sink("out", "#input", "monitor")
    val testJsonData = TestData(
      """{
        | "id": "1",
        | "field": "11"
        |}
        |
        |
        |{
        | "id": "2",
        | "field": "22"
        |}
        |
        |{
        | "id": "3",
        | "field": "33"
        |}
        |""".stripMargin)

    val results = FlinkTestMain.run(modelData, marshall(process), testJsonData, FlinkTestConfiguration.configuration(), identity)

    results.nodeResults("id") should have size 3
    results.mockedResults("out") shouldBe
      List(
        MockedResult("proc1-id-0-0", "monitor", "SimpleJsonRecord(1,11)"),
        MockedResult("proc1-id-0-1", "monitor", "SimpleJsonRecord(2,22)"),
        MockedResult("proc1-id-0-2", "monitor", "SimpleJsonRecord(3,33)")
      )
  }

  test("give meaningful error messages for sink errors") {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .sink("out", "#input", "sinkForInts")

    val run = Future {
      FlinkTestMain.run(modelData, marshall(process), TestData("2|2|2|3|4|5|6"), FlinkTestConfiguration.configuration(), identity)
    }

    val results = Await.result(run, 10 seconds)

    results.exceptions should have length 1
    results.exceptions.head.nodeId shouldBe Some("out")
    results.exceptions.head.throwable.getMessage should include ("For input string: ")

    SinkForInts.data should have length 0
  }

  test("be able to test process with signals") {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .customNodeNoOutput("cid", "signalReader")
        .sink("out", "#input.value1", "monitor")

    val input = SimpleRecord("0", 1, "2", new Date(3), Some(4), 5, "6")


    val results = FlinkTestMain.run(modelData, marshall(process), TestData(List("0|1|2|3|4|5|6").mkString("\n")), FlinkTestConfiguration.configuration(), identity)

    val nodeResults = results.nodeResults

    nodeResults("out") shouldBe List(nodeResult(0, "input" -> input))

  }

  test("be able to test process with time windows") {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .customNode("cid", "count", "transformWithTime", "seconds" -> "10")
        .sink("out", "#count", "monitor")

    def recordWithSeconds(duration: FiniteDuration) = s"0|0|0|${duration.toMillis}|0|0|0"

    val results = FlinkTestMain.run(modelData, marshall(process), TestData(List(
      recordWithSeconds(1 second),
      recordWithSeconds(2 second),
      recordWithSeconds(5 second),
      recordWithSeconds(9 second),
      recordWithSeconds(20 second)
    ).mkString("\n")), FlinkTestConfiguration.configuration(), identity)

    val nodeResults = results.nodeResults

    nodeResults("out").map(_.context.variables) shouldBe List(Map("count" -> 4), Map("count" -> 1))

  }

  test("be able to test typed map") {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "typedJsonInput", "type" -> """{"field1": "String", "field2": "java.lang.String"}""")
        .sink("out", "#input.field1 + #input.field2", "monitor")

    val results = FlinkTestMain.run(modelData,
      marshall(process),
      TestData("""{"field1": "abc", "field2": "def"}"""), FlinkTestConfiguration.configuration(), identity)

    results.invocationResults("out").map(_.value) shouldBe List("abcdef")
  }

  test("using dependent services") {
    val countToPass = "15"
    val valueToReturn = "18"

    val process = EspProcessBuilder
      .id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .enricher("dependent", "parsed", "returningDependentTypeService",
        "definition" -> "{'field1', 'field2'}", "toFill" -> "#input.value1.toString()", "count" -> countToPass)
      .sink("out", "#parsed.size + ' ' + #parsed[0].field2", "monitor")

    val results = FlinkTestMain.run(modelData, marshall(process), TestData(s"0|$valueToReturn|2|3|4|5|6"),
        FlinkTestConfiguration.configuration(), identity)

    //here
    results.invocationResults("out").map(_.value) shouldBe List(s"$countToPass $valueToReturn")
  }

  test("switch value should be equal to variable value") {
    import spel.Implicits._

    val process = EspProcessBuilder
      .id("sampleProcess")
      .parallelism(1)
      .exceptionHandler()
      .source("id", "input")
      .switch("switch", "#input.id == 'ala'", "output",
        Case(
          "#output == false",
          GraphBuilder.sink("out", "''", "monitor")
        )
      )

    val recordTrue = "ala|1|2|3|4|5|6"
    val recordFalse = "bela|1|2|3|4|5|6"

    val results = FlinkTestMain.run(modelData, marshall(process), TestData(List(recordTrue, recordFalse).mkString("\n")), FlinkTestConfiguration.configuration(), identity)

    val invocationResults = results.invocationResults

    invocationResults("switch").filter(_.name == "expression").head.value shouldBe true
    invocationResults("switch").filter(_.name == "expression").last.value shouldBe false
    // first record was filtered out
    invocationResults("out").head.contextId shouldBe "sampleProcess-id-0-1"
  }

  //TODO: in the future we should also handle multiple sources tests...
  test("should handle joins for one input (diamond-like) ") {
    val process = EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
      GraphBuilder.source("id", "input")
        .split("split",
          GraphBuilder.filter("left", "#input.id != 'a'").branchEnd("end1", "join1"),
          GraphBuilder.filter("right", "#input.id != 'b'").branchEnd("end2", "join1")
        ),
      GraphBuilder.branch("join1", "joinBranchExpression", Some("input33"),
        List(
          "end1" -> List("value" -> "#input"),
          "end2" -> List("value" -> "#input")
        ))
        .processorEnd("proc2", "logService", "all" -> "#input33.id")
    ))

    val recA = "a|1|2|1|4|5|6"
    val recB = "b|1|2|2|4|5|6"
    val recC = "c|1|2|3|4|5|6"


    val results = FlinkTestMain.run(modelData, marshall(process), TestData(List(recA, recB, recC).mkString("\n")), FlinkTestConfiguration.configuration(), identity)

    //TODO: currently e.g. invocation results will behave strangely in this test, because we duplicate inputs and this results in duplicate context ids...
    results.mockedResults("proc2").map(_.value.asInstanceOf[String]).sorted shouldBe List("a", "b", "c", "c").map(_ + "-collectedDuringServiceInvocation")
  }

  def nodeResult(count: Int, vars: (String, Any)*): NodeResult[Any] =
    NodeResult(ResultContext[Any](s"proc1-id-0-$count", Map(vars: _*)))
}

