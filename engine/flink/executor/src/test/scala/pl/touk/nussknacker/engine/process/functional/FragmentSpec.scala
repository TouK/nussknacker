package pl.touk.nussknacker.engine.process.functional

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.compile.FragmentResolver
import pl.touk.nussknacker.engine.graph.evaluatedparam.BranchParameters
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._

import java.util.Date

class FragmentSpec extends AnyFunSuite with Matchers with ProcessTestHelpers {

  import pl.touk.nussknacker.engine.spel.Implicits._

  test("should accept same id in fragment and main process ") {

    val process = resolve(
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .fragmentOneOut("sub", "fragment1", "output", "fragmentResult", "param" -> "#input.value2")
        .processorEnd("end1", "logService", "all" -> "#input.value2")
    )

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldNot be(Symbol("empty"))
    ProcessTestHelpers.logServiceResultsHolder.results.head shouldBe "a"
  }

  test("should handle split in fragment") {

    val process = resolve(
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .fragmentOneOut("sub", "splitFragment", "output", "fragmentResult", "param" -> "#input.value2")
        .processorEnd("end1", "logService", "all" -> "#input.value2")
    )

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldNot be(Symbol("empty"))
    ProcessTestHelpers.logServiceResultsHolder.results.head shouldBe "a"
  }

  test("be possible to use global vars in fragment") {
    val process = resolve(
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .fragmentOneOut("sub", "fragmentGlobal", "output", "fragmentResult")
        .processorEnd("end1", "logService", "all" -> "#input.value2")
    )

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldNot be(Symbol("empty"))
    ProcessTestHelpers.logServiceResultsHolder.results.head shouldBe "a"
  }

  test("be possible to use diamond fragments") {
    val process = resolve(
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .fragmentOneOut("sub", "diamondFragment", "output33", "fragmentResult", "ala" -> "#input.id")
        .processorEnd("end1", "logService", "all" -> "#input.value2")
    )

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldNot be(Symbol("empty"))
    ProcessTestHelpers.logServiceResultsHolder.results.head shouldBe "a"
  }

  private def resolve(scenario: CanonicalProcess) = {
    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        canonicalnode.FlatNode(
          FragmentInputDefinition("start", List(FragmentParameter("param", FragmentClazzRef[String])))
        ),
        canonicalnode.FilterNode(
          Filter("f1", "#param == 'a'"),
          List(canonicalnode.FlatNode(Sink("end1", SinkRef("monitor", List()))))
        ),
        canonicalnode.FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      List.empty
    )

    val fragmentWithSplit = CanonicalProcess(
      MetaData("splitFragment", FragmentSpecificData()),
      List(
        canonicalnode.FlatNode(
          FragmentInputDefinition("start", List(FragmentParameter("param", FragmentClazzRef[String])))
        ),
        canonicalnode.SplitNode(
          Split("split"),
          List(
            List(canonicalnode.FlatNode(Sink("end1", SinkRef("monitor", List())))),
            List(canonicalnode.FlatNode(FragmentOutputDefinition("out1", "output", List.empty)))
          )
        )
      ),
      List.empty
    )

    val fragmentWithGlobalVar = CanonicalProcess(
      MetaData("fragmentGlobal", FragmentSpecificData()),
      List(
        canonicalnode.FlatNode(FragmentInputDefinition("start", List())),
        canonicalnode.FilterNode(Filter("f1", "#processHelper.constant == 4"), List()),
        canonicalnode.FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      List.empty
    )

    val diamondFragment = CanonicalProcess(
      MetaData("diamondFragment", FragmentSpecificData()),
      List(
        FlatNode(FragmentInputDefinition("start", List(FragmentParameter("ala", FragmentClazzRef[String])))),
        canonicalnode.SplitNode(
          Split("split"),
          List(
            List(
              canonicalnode.FilterNode(Filter("filter2a", "true"), Nil),
              FlatNode(BranchEndData(BranchEndDefinition("end1", "join1")))
            ),
            List(
              canonicalnode.FilterNode(Filter("filter2b", "true"), Nil),
              FlatNode(BranchEndData(BranchEndDefinition("end2", "join1")))
            )
          )
        )
      ),
      List(
        FlatNode(
          Join(
            "join1",
            Some("output"),
            "joinBranchExpression",
            Nil,
            List(
              BranchParameters("end1", List(NodeParameter("value", "#ala"))),
              BranchParameters("end2", List(NodeParameter("value", "#ala")))
            ),
            None
          )
        ),
        FlatNode(FragmentOutputDefinition("output22", "output33", Nil, None))
      ) :: Nil
    )

    val resolved =
      FragmentResolver(List(fragmentWithSplit, fragment, fragmentWithGlobalVar, diamondFragment)).resolve(scenario)

    resolved shouldBe Symbol("valid")
    resolved.toOption.get
  }

}
