package pl.touk.nussknacker.engine.process.functional

import org.scalatest.LoneElement._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ComponentUseContextProvider
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.flink.test.{RecordingExceptionConsumer, RecordingExceptionConsumerProvider}
import pl.touk.nussknacker.engine.flink.util.sink.SingleValueSinkFactory
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._

import java.util.{Date, UUID}

class ProcessSpec extends AnyFunSuite with Matchers with ProcessTestHelpers {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  import SingleValueSinkFactory._

  test("skip null records") {

    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .processorEnd("proc2", "logService", "all" -> "#input".spel)

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0)),
      SimpleRecord("1", 5, "b", new Date(1000)),
      null,
      SimpleRecord("1", 12, "d", new Date(4000)),
      SimpleRecord("1", 14, "d", new Date(10000)),
      null,
      SimpleRecord("1", 20, "d", new Date(10000))
    )

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results should have size 5

  }

  test("ignore disabled sinks") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("id", "input")
      .disabledSink("out", "monitor")

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(scenario, data)

    ProcessTestHelpers.logServiceResultsHolder.results should have size 0
  }

  test("handles lazy params in sinks") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .emptySink("end", "sinkForInts", SingleValueParamName -> "#input.value1 + 4".spel)

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.sinkForIntsResultsHolder.results shouldBe List(7)
  }

  test("allow global vars in source config") {

    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "intInputWithParam", "param" -> "#processHelper.add(2, 3)".spel)
      .processorEnd("proc2", "logService", "all" -> "#input".spel)

    val data = List()

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldBe List(5)
  }

  test("should do simple join") {

    val process = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source("id", "intInputWithParam", "param" -> "#processHelper.add(2, 3)".spel)
          .branchEnd("end1", "join1"),
        GraphBuilder
          .source("id2", "input")
          .branchEnd("end2", "join1"),
        GraphBuilder
          .join("join1", "sampleJoin", Some("input33"), List.empty)
          .processorEnd("proc2", "logService", "all" -> "#input33".spel)
      )

    val rec  = SimpleRecord("1", 3, "a", new Date(0))
    val data = List(rec)

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results.toSet shouldBe Set(5, rec)
  }

  test("should do join with branch expressions") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source("idInt", "intInputWithParam", "param" -> "#processHelper.add(2, 3)".spel)
          .branchEnd("end1", "join1"),
        GraphBuilder
          .source("idOtherInt", "intInputWithParam", "param" -> "15".spel)
          .branchEnd("end2", "join1"),
        GraphBuilder
          .source("id2", "input")
          .branchEnd("end1", "join2"),
        GraphBuilder
          .join(
            "join1",
            "joinBranchExpression",
            Some("input2"),
            List(
              "end1" -> List("value" -> "#input".spel),
              "end2" -> List("value" -> "#input".spel)
            )
          )
          .branchEnd("end2", "join2"),
        GraphBuilder
          .join(
            "join2",
            "joinBranchExpression",
            Some("input3"),
            List(
              "end1" -> List("value" -> "#input".spel),
              "end2" -> List("value" -> "#input2".spel)
            )
          )
          .processorEnd("proc2", "logService", "all" -> "#input3".spel)
      )

    val rec  = SimpleRecord("1", 3, "a", new Date(0))
    val data = List(rec)

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results.toSet shouldBe Set(5, 15, rec)
  }

  test("should handle diamond-like process") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source("id", "input")
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
          .processorEnd("proc2", "logService", "all" -> "#input33".spel)
      )

    val recA = SimpleRecord("a", 3, "a", new Date(1))
    val recB = SimpleRecord("b", 3, "a", new Date(2))
    val recC = SimpleRecord("c", 3, "a", new Date(3))

    val data = List(recA, recB, recC)

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results
      .sortBy(_.asInstanceOf[SimpleRecord].date) shouldBe List(recA, recB, recC, recC)
  }

  test("usage of scala option parameters in services") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .buildSimpleVariable("sampleVar", "var", "#processHelper.scalaOptionValue".spel)
      .enricher("enrich", "enriched", "serviceAcceptingOptionalValue", "scalaOptionParam" -> "#var".spel)
      .processorEnd("proc2", "logService", "all" -> "#enriched".spel)

    val data = List(
      SimpleRecord("a", 3, "3", new Date(1))
    )

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldBe List(Some("" + ProcessHelper.constant))
  }

  test("usage of java optional parameters in eager parameters") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .emptySink("sink", "eagerOptionalParameterSink", "optionalStringParam" -> "#processHelper.javaOptionalValue".spel)

    val data = List(
      SimpleRecord("a", 3, "3", new Date(1))
    )

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.eagerOptionalParameterSinkResultsHolder.results shouldBe List("" + ProcessHelper.constant)
  }

  test("usage of TypedMap in method parameters") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .buildSimpleVariable("extractedVar", "extractedVar", "#processHelper.extractProperty(#typedMap, 'aField')".spel)
      .processorEnd("proc2", "logService", "all" -> "#extractedVar".spel)

    val data = List(
      SimpleRecord("a", 3, "3", new Date(1))
    )

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldBe List("123")
  }

  test("Open/close only used services") {

    val processWithService = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .processor("processor1", "eagerLifecycleService", "name" -> "'1'".spel)
      // just to test we open also in different process parts
      .customNodeNoOutput("custom", "customFilter", "input" -> "''".spel, "stringVal" -> "''".spel)
      .processor("processor2", "eagerLifecycleService", "name" -> "'2'".spel)
      .processorEnd("enricher", "lifecycleService")

    val processWithoutService = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .processorEnd("proc2", "logService", "all" -> "''".spel)

    LifecycleService.reset()
    EagerLifecycleService.reset()

    processInvoker.invokeWithSampleData(processWithService, Nil)
    LifecycleService.opened shouldBe true
    LifecycleService.closed shouldBe true
    EagerLifecycleService.opened shouldBe true
    EagerLifecycleService.closed shouldBe true

    val openedInvokers = EagerLifecycleService.list.filter(_._2.opened == true)
    openedInvokers.map(_._1).toSet shouldEqual Set("1", "2")
    openedInvokers.foreach { cl =>
      cl._2.closed shouldBe true
    }

    LifecycleService.reset()
    EagerLifecycleService.reset()
    processInvoker.invokeWithSampleData(processWithoutService, Nil)
    LifecycleService.opened shouldBe false
    LifecycleService.closed shouldBe false
    EagerLifecycleService.list shouldBe Symbol("empty")
  }

  test("should have correct run mode") {
    val process = ScenarioBuilder
      .streaming("proc")
      .source("start", "input")
      .enricher("componentUseContext", "componentUseContext", "returningComponentUseContextService")
      .emptySink("out", "sinkForStrings", SingleValueParamName -> "#componentUseContext.toString".spel)

    val data = List(
      SimpleRecord("a", 1, "a", new Date(1))
    )

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.sinkForStringsResultsHolder.results.loneElement shouldBe ComponentUseContext
      .LiveRuntime(None)
      .toString
  }

  test("should handle errors on branches after split independently") {
    val data = List(SimpleRecord("a", 1, "a", new Date(1)))

    val process = ScenarioBuilder
      .streaming("proc")
      .source("start", "input")
      .split(
        "split",
        GraphBuilder
          .enricher("throwingNonTransientErrorsNodeId", "out", "throwingNonTransientErrors", "throw" -> "true".spel)
          .emptySink("out1", "sinkForStrings", SingleValueParamName -> "'a'".spel),
        GraphBuilder
          .emptySink("out2", "sinkForStrings", SingleValueParamName -> "'b'".spel)
      )

    // we test both sync and async to be sure collecting is handled correctly
    List(true, false).foreach { useAsync =>
      ProcessTestHelpers.sinkForStringsResultsHolder.clear()

      val additionalFields = process.metaData.additionalFields

      val scenarioToUse = process.copy(metaData =
        process.metaData
          .copy(additionalFields =
            additionalFields.copy(properties =
              additionalFields.properties ++ Map(StreamMetaData.useAsyncInterpretationName -> useAsync.toString)
            )
          )
      )

      val runId = UUID.randomUUID().toString
      val cfg   = RecordingExceptionConsumerProvider.configWithProvider(config, consumerId = runId)
      processInvoker.invokeWithSampleData(scenarioToUse, data, cfg)

      val exception = RecordingExceptionConsumer.exceptionsFor(runId).loneElement
      exception.throwable shouldBe a[NonTransientException]
      exception.nodeComponentInfo shouldBe Some(
        NodeComponentInfo("throwingNonTransientErrorsNodeId", ComponentType.Service, "throwingNonTransientErrors")
      )
      ProcessTestHelpers.sinkForStringsResultsHolder.results.loneElement shouldBe "b"
    }

  }

}
