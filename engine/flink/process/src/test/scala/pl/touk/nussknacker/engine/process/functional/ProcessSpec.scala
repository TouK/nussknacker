package pl.touk.nussknacker.engine.process.functional

import java.util.Date

import cats.data.NonEmptyList
import org.scalatest.{FlatSpec, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.{EndingNode, Sink, Source, SourceNode}
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers.processInvoker
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.spel

class ProcessSpec extends FunSuite with Matchers {

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
      GraphBuilder.source("id", "intInputWithParam", "param" -> "#processHelper.add(2, 3)")
        .branchEnd("end1", "join1"),
      GraphBuilder.source("id2", "input")
        .branchEnd("end2", "join1"),
      GraphBuilder.branch("join1", "joinBranchExpression", Some("input33"),
        List(
          "end1" -> List("value" -> "#input"),
          "end2" -> List("value" -> "#input")
        ))
        .processorEnd("proc2", "logService", "all" -> "#input33")
    ))

    val rec = SimpleRecord("1", 3, "a", new Date(0))
    val data = List(rec)

    processInvoker.invokeWithSampleData(process, data)

    MockService.data.toSet shouldBe Set(5, rec)
  }

}
