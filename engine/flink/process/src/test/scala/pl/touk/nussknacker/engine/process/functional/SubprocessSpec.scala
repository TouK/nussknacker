package pl.touk.nussknacker.engine.process.functional

import java.util.Date

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compile.SubprocessResolver
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter}
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._

class SubprocessSpec extends FunSuite with Matchers with ProcessTestHelpers {

  import pl.touk.nussknacker.engine.spel.Implicits._

  test("should accept same id in subprocess and main process ") {

    val process = resolve(EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .subprocessOneOut("sub", "subProcess1", "output", "param" -> "#input.value2")
      .processorEnd("end1", "logService", "all" -> "#input.value2"))

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldNot be('empty)
    MockService.data.head shouldBe "a"
  }

  test("should handle split in subprocess") {

    val process = resolve(EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .subprocessOneOut("sub", "splitSubprocess", "output", "param" -> "#input.value2")
      .processorEnd("end1", "logService", "all" -> "#input.value2"))

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldNot be('empty)
    MockService.data.head shouldBe "a"
  }

  test("be possible to use global vars in subprocess") {
    val process = resolve(EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .subprocessOneOut("sub", "subProcessGlobal", "output")
      .processorEnd("end1", "logService", "all" -> "#input.value2"))

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldNot be('empty)
    MockService.data.head shouldBe "a"
  }

  test("be possible to use diamond subprocesses") {
    val process = resolve(EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .subprocessOneOut("sub", "diamondSubprocess", "output33", "ala" -> "#input.id")
      .processorEnd("end1", "logService", "all" -> "#input.value2"))

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldNot be('empty)
    MockService.data.head shouldBe "a"
  }

  private def resolve(espProcess: EspProcess) = {
    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.FilterNode(Filter("f1", "#param == 'a'"),
        List(canonicalnode.FlatNode(Sink("end1", SinkRef("monitor", List()), Some("'deadEnd'"))))
      ), canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output", List.empty))), None)

    val subprocessWithSplit = CanonicalProcess(MetaData("splitSubprocess", StreamMetaData()), null,
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.SplitNode(Split("split"), List(
          List(canonicalnode.FlatNode(Sink("end1", SinkRef("monitor", List())))),
          List(canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output", List.empty)))
        ))
      ), None)

    val subprocessWithGlobalVar = CanonicalProcess(MetaData("subProcessGlobal", StreamMetaData()), null,
          List(
            canonicalnode.FlatNode(SubprocessInputDefinition("start", List())),
            canonicalnode.FilterNode(Filter("f1", "#processHelper.constant == 4"),
            List()
          ), canonicalnode.FlatNode(SubprocessOutputDefinition("out1", "output", List.empty))), None)

    val diamondSubprocess = CanonicalProcess(MetaData("diamondSubprocess", StreamMetaData()), null,
      List(
        FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("ala", SubprocessClazzRef[String])))),
        canonicalnode.SplitNode(Split("split"),
          List(
            List(canonicalnode.FilterNode(Filter("filter2a", "true"), Nil), FlatNode(BranchEndData(BranchEndDefinition("end1", "join1")))),
            List(canonicalnode.FilterNode(Filter("filter2b", "true"), Nil), FlatNode(BranchEndData(BranchEndDefinition("end2", "join1"))))
          )
        )
      ), Some(List(
        FlatNode(Join("join1", Some("output"), "joinBranchExpression", Nil, List(
          BranchParameters("end1", List(Parameter("value", "#ala"))),
          BranchParameters("end2", List(Parameter("value", "#ala")))
        ), None)),
        FlatNode(SubprocessOutputDefinition("output22", "output33", Nil, None))
      ):: Nil)
    )
    
    val resolved = SubprocessResolver(Set(subprocessWithSplit, subprocess, subprocessWithGlobalVar, diamondSubprocess)).resolve(ProcessCanonizer.canonize(espProcess))
      .andThen(ProcessCanonizer.uncanonize)

    resolved shouldBe 'valid

    resolved.toOption.get
  }


}
