package pl.touk.nussknacker.engine.process.functional

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, NodeId, NodeName}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.{canonicalnode, CanonicalProcess}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.compile.FragmentResolver
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.evaluatedparam.BranchParameters
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.springframework.util.BigDecimalScaleEnsurer

import java.util.Date

class FragmentSpec extends AnyFunSuite with Matchers with ProcessTestHelpers {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  test("should properly convert fragment input to BigDecimal") {

    val process = resolve(
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .fragmentOneOut("sub", "fragmentWithBigDecimalInput", "output", "fragmentResult", "param" -> "1".spel)
        .processorEnd("end1", "logService", "all" -> "#fragmentResult.a".spel)
    )

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)
    val results = ProcessTestHelpers.logServiceResultsHolder.results

    results.size shouldBe 1
    results(0).asInstanceOf[java.math.BigDecimal].scale() shouldBe BigDecimalScaleEnsurer.DEFAULT_BIG_DECIMAL_SCALE
  }

  test("should accept same id in fragment and main process ") {

    val process = resolve(
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .fragmentOneOut("sub", "fragment1", "output", "fragmentResult", "param" -> "#input.value2".spel)
        .processorEnd("end1", "logService", "all" -> "#input.value2".spel)
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
        .fragmentOneOut("sub", "splitFragment", "output", "fragmentResult", "param" -> "#input.value2".spel)
        .processorEnd("end1", "logService", "all" -> "#input.value2".spel)
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
        .processorEnd("end1", "logService", "all" -> "#input.value2".spel)
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
        .fragmentOneOut("sub", "diamondFragment", "output33", "fragmentResult", "ala" -> "#input.id".spel)
        .processorEnd("end1", "logService", "all" -> "#input.value2".spel)
    )

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results shouldNot be(Symbol("empty"))
    ProcessTestHelpers.logServiceResultsHolder.results.head shouldBe "a"
  }

  private def resolve(scenario: CanonicalProcess) = {
    val fragmentWithBigDecimalInput = CanonicalProcess(
      MetaData("fragmentWithBigDecimalInput", FragmentSpecificData()),
      List(
        canonicalnode.FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("param"), FragmentClazzRef[java.math.BigDecimal]))
          )
        ),
        canonicalnode.FlatNode(
          FragmentOutputDefinition(
            NodeId("outB1"),
            NodeName("outB1"),
            "output",
            List(Field("a", Expression.spel("#param")))
          )
        )
      ),
      List.empty
    )

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        canonicalnode.FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode.FilterNode(
          Filter(NodeId("f1"), NodeName("f1"), "#param == 'a'".spel),
          List(canonicalnode.FlatNode(Sink(NodeId("end1"), NodeName("end1"), SinkRef("monitor", List()))))
        ),
        canonicalnode.FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List.empty))
      ),
      List.empty
    )

    val fragmentWithSplit = CanonicalProcess(
      MetaData("splitFragment", FragmentSpecificData()),
      List(
        canonicalnode.FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode.SplitNode(
          Split(NodeId("split"), NodeName("split")),
          List(
            List(canonicalnode.FlatNode(Sink(NodeId("end1"), NodeName("end1"), SinkRef("monitor", List())))),
            List(
              canonicalnode.FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List.empty))
            )
          )
        )
      ),
      List.empty
    )

    val fragmentWithGlobalVar = CanonicalProcess(
      MetaData("fragmentGlobal", FragmentSpecificData()),
      List(
        canonicalnode.FlatNode(FragmentInputDefinition(NodeId("start"), NodeName("start"), List())),
        canonicalnode.FilterNode(Filter(NodeId("f1"), NodeName("f1"), "#processHelper.constant == 4".spel), List()),
        canonicalnode.FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List.empty))
      ),
      List.empty
    )

    val diamondFragment = CanonicalProcess(
      MetaData("diamondFragment", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode.SplitNode(
          Split(NodeId("split"), NodeName("split")),
          List(
            List(
              canonicalnode.FilterNode(Filter(NodeId("filter2a"), NodeName("filter2a"), "true".spel), Nil),
              FlatNode(BranchEndData(BranchEndDefinition("end1", "join1")))
            ),
            List(
              canonicalnode.FilterNode(Filter(NodeId("filter2b"), NodeName("filter2b"), "true".spel), Nil),
              FlatNode(BranchEndData(BranchEndDefinition("end2", "join1")))
            )
          )
        )
      ),
      List(
        FlatNode(
          Join(
            NodeId("join1"),
            NodeName("join1"),
            Some("output"),
            "joinBranchExpression",
            Nil,
            List(
              BranchParameters("end1", List(NodeParameter(ParameterName("value"), "#ala".spel))),
              BranchParameters("end2", List(NodeParameter(ParameterName("value"), "#ala".spel)))
            ),
            None
          )
        ),
        FlatNode(FragmentOutputDefinition(NodeId("output22"), NodeName("output22"), "output33", Nil, None))
      ) :: Nil
    )

    val resolved =
      FragmentResolver(
        List(fragmentWithBigDecimalInput, fragmentWithSplit, fragment, fragmentWithGlobalVar, diamondFragment)
      ).resolve(scenario)

    resolved shouldBe Symbol("valid")
    resolved.toOption.get
  }

}
