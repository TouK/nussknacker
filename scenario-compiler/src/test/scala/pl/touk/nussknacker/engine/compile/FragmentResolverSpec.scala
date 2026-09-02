package pl.touk.nussknacker.engine.compile

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import org.scalatest.{Inside, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, NodeId, NodeName}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.build.GraphBuilder.Creator
import pl.touk.nussknacker.engine.canonicalgraph.{canonicalnode, CanonicalProcess}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{FlatNode, Fragment}
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.sink.SinkRef

class FragmentResolverSpec extends AnyFunSuite with Matchers with Inside with OptionValues {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  test("resolve simple process") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "source1")
      .fragmentOneOut("sub", "fragment1", "output", "fragmentResult", "ala" -> "'makota'".spel)
      .fragmentOneOut("sub2", "fragment1", "output", "fragmentResult", "ala" -> "'makota'".spel)
      .emptySink("sink", "sink1")

    val suprocessParameters = List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String]))

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(FragmentInputDefinition(NodeId("start"), NodeName("start"), suprocessParameters)),
        canonicalnode.FilterNode(Filter(NodeId("f1"), NodeName("f1"), "false".spel), List()),
        FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List.empty))
      ),
      List.empty
    )

    val resolvedValidated = FragmentResolver(List(fragment)).resolve(process)

    resolvedValidated shouldBe Symbol("valid")
    val resolved = resolvedValidated.toOption.get

    resolved.nodes.filter(_.isInstanceOf[Fragment]) shouldBe Symbol("empty")
    resolved.nodes.find(_.id == NodeId("f1")) shouldBe Symbol("empty")
    resolved.nodes.find(_.id == NodeId("sub-f1")) shouldBe Symbol("defined")
    resolved.nodes.find(_.id == NodeId("sub")).get.data should matchPattern {
      case FragmentInput(_, _, _, _, _, Some(fragmentParameters)) =>
    }
    resolved.nodes.find(_.id == NodeId("sub")).get.data
    resolved.nodes.find(_.id == NodeId("sub2-f1")) shouldBe Symbol("defined")
    resolved.nodes.find(_.id == NodeId("sub-f1")).map(_.data.name) shouldBe Some(NodeName("sub-f1"))
    resolved.nodes.find(_.id == NodeId("sub2-f1")).map(_.data.name) shouldBe Some(NodeName("sub2-f1"))
  }

  test("resolve fragment used inside an additional output of a multi-output custom node") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "source1")
      .customNodeWithOutputs(
        "custom",
        Some("out"),
        "customTransformer",
        List(
          "main" -> GraphBuilder.emptySink("mainSink", "sink1").get,
          "rejected" -> GraphBuilder
            .fragmentOneOut("sub", "fragment1", "output", "fragmentResult", "ala" -> "'makota'".spel)
            .emptySink("rejectedSink", "sink1")
            .get
        )
      )

    val suprocessParameters = List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String]))

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(FragmentInputDefinition(NodeId("start"), NodeName("start"), suprocessParameters)),
        canonicalnode.FilterNode(Filter(NodeId("f1"), NodeName("f1"), "false".spel), List()),
        FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List.empty))
      ),
      List.empty
    )

    val resolvedValidated = FragmentResolver(List(fragment)).resolve(process)

    resolvedValidated shouldBe Symbol("valid")
    val resolved = resolvedValidated.toOption.get

    val custom = resolved.nodes.collectFirst { case c: canonicalnode.CustomNodeWithOutputs => c }.get
    custom.outputs.map(_.name).toList should contain("rejected")

    // the fragment inside the output is inlined, its inner filter prefixed with the fragment node id
    val rejectedNodes = custom.outputs.find(_.name == "rejected").value.nodes
    val outputNodeIds = rejectedNodes.flatMap(canonicalnode.collectAllNodes).map(_.id.value)
    outputNodeIds should contain("sub-f1")
    rejectedNodes.exists(_.isInstanceOf[canonicalnode.Fragment]) shouldBe false
  }

  test("resolve fragment containing a multi-output custom node in its body") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "source1")
      .fragment(
        "sub",
        "fragment1",
        List("ala"   -> "'makota'".spel),
        Map("output" -> "fragmentResult", "rejectedOutput" -> "rejectedResult"),
        Map(
          "output"         -> GraphBuilder.emptySink("mainSink", "sink1"),
          "rejectedOutput" -> GraphBuilder.emptySink("rejectedSink", "sink1")
        )
      )

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode.CustomNodeWithOutputs(
          CustomNode(NodeId("multi"), NodeName("multi"), Some("multiOut"), "customTransformer", List()),
          List(
            canonicalnode.Output(
              "main",
              List(FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List.empty)))
            ),
            canonicalnode.Output(
              "rejected",
              List(
                FlatNode(
                  FragmentOutputDefinition(NodeId("rejectedOut"), NodeName("rejectedOut"), "rejectedOutput", List.empty)
                )
              )
            )
          )
        )
      ),
      List.empty
    )

    val resolvedValidated = FragmentResolver(List(fragment)).resolve(process)

    resolvedValidated shouldBe Symbol("valid")
    val resolved = resolvedValidated.toOption.get

    resolved.nodes.exists(_.isInstanceOf[Fragment]) shouldBe false

    // the multi-output node is inlined with its id prefixed, keeping the outputs structure
    val custom = resolved.nodes.collectFirst { case c: canonicalnode.CustomNodeWithOutputs => c }.get
    custom.data.id shouldBe NodeId("sub-multi")

    // the fragment output inside the additional output branch is rewired to the scenario's continuation
    val rejectedNodeIds =
      custom.outputs.find(_.name == "rejected").value.nodes.flatMap(canonicalnode.collectAllNodes).map(_.id.value)
    rejectedNodeIds should contain("rejectedSink")

    // and the main continuation's fragment output leads to the scenario's main sink
    resolved.nodes.flatMap(canonicalnode.collectAllNodes).map(_.id.value) should contain("mainSink")
  }

  test("resolve nested fragments") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "source1")
      .fragmentOneOut("sub", "fragment1", "output", "fragmentResult", "param" -> "'makota'".spel)
      .emptySink("sink", "sink1")

    val fragment = CanonicalProcess(
      MetaData("fragment2", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode
          .FilterNode(
            Filter(NodeId("f1"), NodeName("f1"), "#param == 'a'".spel),
            List(FlatNode(Sink(NodeId("deadEnd"), NodeName("deadEnd"), SinkRef("sink1", List()))))
          ),
        FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List.empty))
      ),
      List.empty
    )

    val nested = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode.Fragment(
          FragmentInput(
            NodeId("sub2"),
            NodeName("sub2"),
            FragmentRef("fragment2", List(NodeParameter(ParameterName("param"), "#param".spel)))
          ),
          Map(
            "output" -> List(
              FlatNode(FragmentOutputDefinition(NodeId("sub2Out"), NodeName("sub2Out"), "output", List.empty))
            )
          )
        )
      ),
      List.empty
    )

    val resolvedValidated = FragmentResolver(List(fragment, nested)).resolve(process)

    resolvedValidated shouldBe Symbol("valid")
    val resolved = resolvedValidated.toOption.get

    resolved.nodes.filter(_.isInstanceOf[Fragment]) shouldBe Symbol("empty")
    resolved.nodes.find(_.id == NodeId("f1")) shouldBe Symbol("empty")
    resolved.nodes.find(_.id == NodeId("sub2")) shouldBe Symbol("empty")
    resolved.nodes.find(_.id == NodeId("sub2-f1")) shouldBe Symbol("empty")

    resolved.nodes.find(_.id == NodeId("sub")) shouldBe Symbol("defined")
    resolved.nodes.find(_.id == NodeId("sub-sub2")) shouldBe Symbol("defined")
    resolved.nodes.find(_.id == NodeId("sub-sub2-f1")) shouldBe Symbol("defined")
  }

  test("not resolve fragment with bad outputs") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "source1")
      .fragmentOneOut("sub", "fragment1", "output", "fragmentResult", "ala" -> "'makota'".spel)
      .emptySink("sink", "sink1")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode.FilterNode(Filter(NodeId("f1"), NodeName("f1"), "false".spel), List()),
        FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "badoutput", List.empty))
      ),
      List.empty
    )

    val resolvedValidated = FragmentResolver(List(fragment)).resolve(process)

    resolvedValidated shouldBe Invalid(
      NonEmptyList.of(FragmentOutputNotDefined("badoutput", Set(NodeId("sub-out1"), NodeId("sub"))))
    )

  }

  test("not disable fragment with many outputs") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "source1")
      .fragmentDisabledManyOutputs(
        "sub",
        "fragment1",
        List("ala" -> "'makota'".spel),
        Map(
          "output1" -> GraphBuilder.emptySink("sink1", "out1"),
          "output2" -> GraphBuilder.emptySink("sink2", "out2")
        )
      )

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode.FilterNode(Filter(NodeId("f1"), NodeName("f1"), "false".spel), List()),
        canonicalnode.SplitNode(
          Split(NodeId("s"), NodeName("s")),
          List(
            List(FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List.empty))),
            List(FlatNode(FragmentOutputDefinition(NodeId("out2"), NodeName("out2"), "output", List.empty)))
          )
        )
      ),
      List.empty
    )

    val resolvedValidated = FragmentResolver(List(fragment)).resolve(process)

    resolvedValidated shouldBe Invalid(NonEmptyList.of(DisablingManyOutputsFragment(NodeId("sub"))))

  }

  test("not disable fragment with no outputs") {

    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "source1")
      .fragmentDisabledEnd("sub", "fragment1")

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode.FilterNode(Filter(NodeId("f1"), NodeName("f1"), "false".spel), List()),
        FlatNode(
          Sink(
            NodeId("disabledFragmentMockedSink"),
            NodeName("disabledFragmentMockedSink"),
            SinkRef("disabledFragmentMockedSink", List())
          )
        )
      ),
      List.empty
    )

    val resolvedValidated = FragmentResolver(List(fragment)).resolve(process)

    resolvedValidated shouldBe Invalid(NonEmptyList.of(DisablingNoOutputsFragment(NodeId("sub"))))

  }

  test("inline disabled fragment without inner nodes") {
    val processWithEmptyFragment = ScenarioBuilder
      .streaming("test")
      .source("source", "source1")
      .fragmentOneOut("sub", "emptyFragment", "output", "fragmentResult", "ala" -> "'makota'".spel)
      .filter("d", "true".spel)
      .emptySink("sink", "sink1")

    val processWithDisabledFragment =
      ScenarioBuilder
        .streaming("test")
        .source("source", "source1")
        .fragmentDisabled("sub", "fragment1", "output", "ala" -> "'makota'".spel)
        .filter("d", "true".spel)
        .emptySink("sink", "sink1")

    val emptyFragment = CanonicalProcess(
      MetaData("emptyFragment", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String]))
          )
        ),
        FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List.empty))
      ),
      List.empty
    )
    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            NodeId("start"),
            NodeName("start"),
            List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String]))
          )
        ),
        canonicalnode.FilterNode(Filter(NodeId("f1"), NodeName("f1"), "false".spel), List()),
        FlatNode(FragmentOutputDefinition(NodeId("out1"), NodeName("out1"), "output", List.empty))
      ),
      List.empty
    )
    val resolver = FragmentResolver(List(fragment, emptyFragment))
    val pattern: PartialFunction[ValidatedNel[ProcessCompilationError, CanonicalProcess], _] = {
      case Valid(CanonicalProcess(_, flatNodes, _, _, _)) =>
        flatNodes(0) match {
          case FlatNode(Source(id, _, _, _)) =>
            id shouldBe NodeId("source")
          case e => fail(e.toString)
        }
        flatNodes(1) match {
          case FlatNode(FragmentInput(id, _, _, _, _, _)) =>
            id shouldBe NodeId("sub")
          case e => fail(e.toString)
        }
        flatNodes(2) match {
          case FlatNode(FragmentUsageOutput(_, _, _, _, _, _)) =>
          // output id is unpredictable
          case e => fail(e.toString)
        }
        flatNodes(3) match {
          case canonicalnode.FilterNode(Filter(id, _, _, _, _), _) =>
            id shouldBe NodeId("d")
          case e => fail(e.toString)
        }
        flatNodes(4) match {
          case FlatNode(node) => node.id shouldBe NodeId("sink")
          case e              => fail(e.toString)
        }

    }
    inside(resolver.resolve(processWithEmptyFragment))(pattern)
    inside(resolver.resolve(processWithDisabledFragment))(pattern)
  }

  test("resolve fragment at end of process") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "source1")
      .fragmentEnd("sub", "fragment1", "ala" -> "'makota'".spel)

    val fragment = ScenarioBuilder
      .fragment("fragment1", "ala" -> classOf[String])
      .filter("f1", "false".spel)
      .emptySink("end", "sink1")

    val resolvedValidated = FragmentResolver(List(fragment)).resolve(process)

    resolvedValidated shouldBe Symbol("valid")
    val resolved = resolvedValidated.toOption.get

    resolved.nodes.filter(_.isInstanceOf[Fragment]) shouldBe Symbol("empty")
  }

  test("detect unknown fragment") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .fragmentOneOut("nodeFragmentId", "fragmentId", "fragmentResult", "output")
      .emptySink("id2", "sink")

    val resolvedValidated = FragmentResolver(List.empty).resolve(process)

    resolvedValidated shouldBe Invalid(
      NonEmptyList.of(
        UnknownFragment(id = "fragmentId", nodeId = NodeId("nodeFragmentId"), nodeName = NodeName("nodeFragmentId"))
      )
    )
  }

  test("should resolve diamond fragments") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("source", "source1")
      .fragment(
        "sub",
        "fragment1",
        List("ala"   -> "'makota'".spel),
        Map("output" -> "fragmentResult"),
        Map(
          "output" ->
            GraphBuilder.emptySink("sink", "type")
        )
      )

    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
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
              FlatNode(Filter(NodeId("filter2a"), NodeName("filter2a"), "false".spel)),
              FlatNode(BranchEndData(BranchEndDefinition("join2a", "join1")))
            ),
            List(
              FlatNode(Filter(NodeId("filter2b"), NodeName("filter2b"), "false".spel)),
              FlatNode(BranchEndData(BranchEndDefinition("join2b", "join1")))
            )
          )
        )
      ),
      List(
        FlatNode(Join(NodeId("join1"), NodeName("join1"), None, "union", Nil, Nil, None)),
        FlatNode(FragmentOutputDefinition(NodeId("output"), NodeName("output"), "output", Nil, None))
      ) :: Nil
    )

    val resolvedValidated = FragmentResolver(List(fragment)).resolve(process).toOption.get.allStartNodes
    resolvedValidated should have length 2
  }

  test("handle fragment with empty outputs") {
    val fragment = ScenarioBuilder
      .fragment("fragment1")
      .split(
        "split",
        GraphBuilder.fragmentOutput("end1", "output1", "field" -> "false".spel),
        GraphBuilder.fragmentOutput("end2", "output2"),
      )
    val scenario = ScenarioBuilder
      .streaming("scenario1")
      .source("source", "source1")
      .fragment(
        "fragment",
        "fragment1",
        Nil,
        Map("output1" -> "outVar1"),
        Map(
          "output1" -> GraphBuilder.emptySink("id1", "sink"),
          "output2" -> GraphBuilder.emptySink("id2", "sink"),
        )
      )

    val resolvedValidated = FragmentResolver(List(fragment)).resolve(scenario)
    resolvedValidated shouldBe Symbol("valid")

  }

  test("detect multiple ends with same name") {
    val fragment = ScenarioBuilder
      .fragment("fragment1")
      .split("split", GraphBuilder.fragmentOutput("end1", "output1"), GraphBuilder.fragmentOutput("end2", "output1"))
    val scenario = ScenarioBuilder
      .streaming("scenario1")
      .source("source", "source1")
      .fragmentOneOut("fragment", "fragment1", "output1", "outVar1")
      .emptySink("id1", "sink")

    val resolvedValidated = FragmentResolver(List(fragment)).resolve(scenario)
    resolvedValidated shouldBe Invalid(
      NonEmptyList.of(DuplicateFragmentOutputNamesInScenario("output1", NodeId("fragment")))
    )

  }

  // FIXME: not sure if it's good way.
  private implicit class DisabledFragment[R](builder: GraphBuilder[R]) extends GraphBuilder[R] {

    def fragmentDisabled(
        id: String,
        fragmentId: String,
        output: String,
        params: (String, Expression)*
    ): GraphBuilder[R] =
      build(node =>
        builder.creator(
          Some(
            FragmentNode(
              FragmentInput(
                NodeId(id),
                NodeName(id),
                FragmentRef(
                  fragmentId,
                  params.map { case (name, value) => NodeParameter(ParameterName(name), value) }.toList
                ),
                isDisabled = Some(true)
              ),
              Map(output -> node)
            )
          )
        )
      )

    def fragmentDisabledManyOutputs(
        id: String,
        fragmentId: String,
        params: List[(String, Expression)],
        outputs: Map[String, Option[SubsequentNode]]
    ): R =
      creator(
        Some(
          FragmentNode(
            FragmentInput(
              NodeId(id),
              NodeName(id),
              FragmentRef(fragmentId, params.map { case (name, value) => NodeParameter(ParameterName(name), value) }),
              isDisabled = Some(true)
            ),
            outputs
          )
        )
      )

    def fragmentDisabledEnd(id: String, fragmentId: String, params: (String, Expression)*): R =
      creator(
        Some(
          FragmentNode(
            FragmentInput(
              NodeId(id),
              NodeName(id),
              FragmentRef(
                fragmentId,
                params.map { case (name, value) => NodeParameter(ParameterName(name), value) }.toList
              ),
              isDisabled = Some(true)
            ),
            Map()
          )
        )
      )

    override def build(inner: Creator[R]): GraphBuilder[R] = builder.build(inner)

    override def creator: Creator[R] = builder.creator
  }

}
