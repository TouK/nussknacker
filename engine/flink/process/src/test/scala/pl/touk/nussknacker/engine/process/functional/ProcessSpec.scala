package pl.touk.nussknacker.engine.process.functional

import java.util.Date

import cats.data.NonEmptyList
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.LoneElement._
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.api.process.RunMode
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.{EndingNode, Sink, Source, SourceNode}
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.spel

class ProcessSpec extends FunSuite with Matchers with ProcessTestHelpers {

  import spel.Implicits._

  test("skip null records") {

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .processorEnd("proc2", "logService", "all" -> "#input")


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

    MockService.data should have size 5

  }

  test("ignore disabled sinks") {
    val processRoot = SourceNode(
      Source("id", SourceRef("input", List.empty)),
      EndingNode(Sink("out", SinkRef("monitor", List.empty), isDisabled = Some(true)))
    )
    val process = EspProcess(MetaData("", StreamMetaData()), ExceptionHandlerRef(List.empty), NonEmptyList.of(processRoot))

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    MockService.data should have size 0
  }

  test("handles lazy params in sinks") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .emptySink("end", "lazyParameterSink", "intParam" -> "#input.value1 + 4")


    val data = List(
      SimpleRecord("1", 3, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    SinkForInts.data shouldBe List(7)
  }


  test("allow global vars in source config") {

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "intInputWithParam", "param" -> "#processHelper.add(2, 3)")
      .processorEnd("proc2", "logService", "all" -> "#input")


    val data = List()

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldBe List(5)
  }

  test("should do simple join") {

    val process = EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
      GraphBuilder.source("id", "intInputWithParam", "param" -> "#processHelper.add(2, 3)")
        .branchEnd("end1", "join1"),
      GraphBuilder.source("id2", "input")
        .branchEnd("end2", "join1"),
      GraphBuilder.branch("join1", "sampleJoin", Some("input33"), List.empty).processorEnd("proc2", "logService", "all" -> "#input33")
    ))

    val rec = SimpleRecord("1", 3, "a", new Date(0))
    val data = List(rec)

    processInvoker.invokeWithSampleData(process, data)

    MockService.data.toSet shouldBe Set(5, rec)

  }

  test("should do join with branch expressions") {
    val process = EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
      GraphBuilder.source("idInt", "intInputWithParam", "param" -> "#processHelper.add(2, 3)")
        .branchEnd("end1", "join1"),
      GraphBuilder.source("idOtherInt", "intInputWithParam", "param" -> "15")
        .branchEnd("end2", "join1"),
      
      GraphBuilder.source("id2", "input")
        .branchEnd("end1", "join2"),
      GraphBuilder.branch("join1", "joinBranchExpression", Some("input2"),
        List(
          "end1" -> List("value" -> "#input"),
          "end2" -> List("value" -> "#input")
        )).branchEnd("end2", "join2"),

      GraphBuilder.branch("join2", "joinBranchExpression", Some("input3"),
        List(
          "end1" -> List("value" -> "#input"),
          "end2" -> List("value" -> "#input2")
        ))
        .processorEnd("proc2", "logService", "all" -> "#input3")
    ))

    val rec = SimpleRecord("1", 3, "a", new Date(0))
    val data = List(rec)

    processInvoker.invokeWithSampleData(process, data)

    MockService.data.toSet shouldBe Set(5, 15, rec)
  }

  test("should handle diamond-like process") {
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
        .processorEnd("proc2", "logService", "all" -> "#input33")
    ))

    val recA = SimpleRecord("a", 3, "a", new Date(1))
    val recB = SimpleRecord("b", 3, "a", new Date(2))
    val recC = SimpleRecord("c", 3, "a", new Date(3))

    val data = List(recA, recB, recC)

    processInvoker.invokeWithSampleData(process, data)

    MockService.data.sortBy(_.asInstanceOf[SimpleRecord].date) shouldBe List(recA, recB, recC, recC)
  }

  test("usage of scala option parameters in services") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .buildSimpleVariable("sampleVar", "var", "#processHelper.scalaOptionValue")
      .enricher("enrich", "enriched", "serviceAcceptingOptionalValue", "scalaOptionParam" -> "#var")
      .processorEnd("proc2", "logService", "all" -> "#enriched")

    val data = List(
      SimpleRecord("a", 3, "3", new Date(1))
    )

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldBe List(Some("" + ProcessHelper.constant))
  }

  test("usage of java optional parameters in eager parameters") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .emptySink("sink", "eagerOptionalParameterSink", "optionalStringParam" -> "#processHelper.javaOptionalValue")

    val data = List(
      SimpleRecord("a", 3, "3", new Date(1))
    )

    processInvoker.invokeWithSampleData(process, data)

    EagerOptionalParameterSinkFactory.data shouldBe List("" + ProcessHelper.constant)
  }

  test("usage of TypedMap in method parameters") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .buildSimpleVariable("extractedVar", "extractedVar", "#processHelper.extractProperty(#typedMap, 'aField')")
      .processorEnd("proc2", "logService", "all" -> "#extractedVar")

    val data = List(
      SimpleRecord("a", 3, "3", new Date(1))
    )

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldBe List("123")
  }

  test("Open/close only used services") {

    val processWithService = EspProcessBuilder.id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .processor("processor1", "eagerLifecycleService", "name" -> "'1'")
        //just to test we open also in different process parts
        .customNodeNoOutput("custom", "customFilter", "input" -> "''", "stringVal" -> "''")
        .processor("processor2", "eagerLifecycleService", "name" -> "'2'")
        .processorEnd("enricher", "lifecycleService")

    val processWithoutService = EspProcessBuilder.id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .processorEnd("proc2", "logService", "all" -> "''")


    LifecycleService.reset()
    EagerLifecycleService.reset()

    processInvoker.invokeWithSampleData(processWithService, Nil)
    LifecycleService.opened shouldBe true
    LifecycleService.closed shouldBe true
    EagerLifecycleService.opened shouldBe true
    EagerLifecycleService.closed shouldBe true

    val openedInvokers = EagerLifecycleService.list.filter(_._2.opened == true)
    openedInvokers.map(_._1).toSet == Set("1", "2")
    openedInvokers.foreach { cl =>
      cl._2.closed shouldBe true
    }

    LifecycleService.reset()
    EagerLifecycleService.reset()
    processInvoker.invokeWithSampleData(processWithoutService, Nil)
    LifecycleService.opened shouldBe false
    LifecycleService.closed shouldBe false
    EagerLifecycleService.list shouldBe 'empty
  }

  test("should have correct run mode") {
    val process = EspProcessBuilder
      .id("proc")
      .exceptionHandler()
      .source("start", "input")
      .enricher("runMode", "runMode", "returningRunModeService")
      .sink("out", "#runMode.toString", "sinkForStrings")

    val data = List(
      SimpleRecord("a", 1, "a", new Date(1))
    )

    processInvoker.invokeWithSampleData(process, data)

    SinkForStrings.data.loneElement shouldBe RunMode.Normal.toString
  }
}
