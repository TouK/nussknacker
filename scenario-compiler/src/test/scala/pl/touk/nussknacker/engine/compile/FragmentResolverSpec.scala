package pl.touk.nussknacker.engine.compile

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
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

class FragmentResolverSpec extends AnyFunSuite with Matchers with Inside {

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
        FlatNode(FragmentInputDefinition("start", suprocessParameters)),
        canonicalnode.FilterNode(Filter("f1", "false".spel), List()),
        FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      List.empty
    )

    val resolvedValidated = FragmentResolver(List(fragment)).resolve(process)

    resolvedValidated shouldBe Symbol("valid")
    val resolved = resolvedValidated.toOption.get

    resolved.nodes.filter(_.isInstanceOf[Fragment]) shouldBe Symbol("empty")
    resolved.nodes.find(_.id == "f1") shouldBe Symbol("empty")
    resolved.nodes.find(_.id == "sub-f1") shouldBe Symbol("defined")
    resolved.nodes.find(_.id == "sub").get.data should matchPattern {
      case FragmentInput(_, _, _, _, Some(fragmentParameters)) =>
    }
    resolved.nodes.find(_.id == "sub").get.data
    resolved.nodes.find(_.id == "sub2-f1") shouldBe Symbol("defined")
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
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String])))
        ),
        canonicalnode
          .FilterNode(Filter("f1", "#param == 'a'".spel), List(FlatNode(Sink("deadEnd", SinkRef("sink1", List()))))),
        FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      List.empty
    )

    val nested = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("param"), FragmentClazzRef[String])))
        ),
        canonicalnode.Fragment(
          FragmentInput("sub2", FragmentRef("fragment2", List(NodeParameter(ParameterName("param"), "#param".spel)))),
          Map("output" -> List(FlatNode(FragmentOutputDefinition("sub2Out", "output", List.empty))))
        )
      ),
      List.empty
    )

    val resolvedValidated = FragmentResolver(List(fragment, nested)).resolve(process)

    resolvedValidated shouldBe Symbol("valid")
    val resolved = resolvedValidated.toOption.get

    resolved.nodes.filter(_.isInstanceOf[Fragment]) shouldBe Symbol("empty")
    resolved.nodes.find(_.id == "f1") shouldBe Symbol("empty")
    resolved.nodes.find(_.id == "sub2") shouldBe Symbol("empty")
    resolved.nodes.find(_.id == "sub2-f1") shouldBe Symbol("empty")

    resolved.nodes.find(_.id == "sub") shouldBe Symbol("defined")
    resolved.nodes.find(_.id == "sub-sub2") shouldBe Symbol("defined")
    resolved.nodes.find(_.id == "sub-sub2-f1") shouldBe Symbol("defined")
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
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String])))
        ),
        canonicalnode.FilterNode(Filter("f1", "false".spel), List()),
        FlatNode(FragmentOutputDefinition("out1", "badoutput", List.empty))
      ),
      List.empty
    )

    val resolvedValidated = FragmentResolver(List(fragment)).resolve(process)

    resolvedValidated shouldBe Invalid(NonEmptyList.of(FragmentOutputNotDefined("badoutput", Set("sub-out1", "sub"))))

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
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String])))
        ),
        canonicalnode.FilterNode(Filter("f1", "false".spel), List()),
        canonicalnode.SplitNode(
          Split("s"),
          List(
            List(FlatNode(FragmentOutputDefinition("out1", "output", List.empty))),
            List(FlatNode(FragmentOutputDefinition("out2", "output", List.empty)))
          )
        )
      ),
      List.empty
    )

    val resolvedValidated = FragmentResolver(List(fragment)).resolve(process)

    resolvedValidated shouldBe Invalid(NonEmptyList.of(DisablingManyOutputsFragment("sub")))

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
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String])))
        ),
        canonicalnode.FilterNode(Filter("f1", "false".spel), List()),
        FlatNode(Sink("disabledFragmentMockedSink", SinkRef("disabledFragmentMockedSink", List())))
      ),
      List.empty
    )

    val resolvedValidated = FragmentResolver(List(fragment)).resolve(process)

    resolvedValidated shouldBe Invalid(NonEmptyList.of(DisablingNoOutputsFragment("sub")))

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
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String])))
        ),
        FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      List.empty
    )
    val fragment = CanonicalProcess(
      MetaData("fragment1", FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String])))
        ),
        canonicalnode.FilterNode(Filter("f1", "false".spel), List()),
        FlatNode(FragmentOutputDefinition("out1", "output", List.empty))
      ),
      List.empty
    )
    val resolver = FragmentResolver(List(fragment, emptyFragment))
    val pattern: PartialFunction[ValidatedNel[ProcessCompilationError, CanonicalProcess], _] = {
      case Valid(CanonicalProcess(_, flatNodes, _)) =>
        flatNodes(0) match {
          case FlatNode(Source(id, _, _)) =>
            id shouldBe "source"
          case e => fail(e.toString)
        }
        flatNodes(1) match {
          case FlatNode(FragmentInput(id, _, _, _, _)) =>
            id shouldBe "sub"
          case e => fail(e.toString)
        }
        flatNodes(2) match {
          case FlatNode(FragmentUsageOutput(_, _, _, _)) =>
          // output id is unpredictable
          case e => fail(e.toString)
        }
        flatNodes(3) match {
          case canonicalnode.FilterNode(Filter(id, _, _, _), _) =>
            id shouldBe "d"
          case e => fail(e.toString)
        }
        flatNodes(4) match {
          case FlatNode(node) => node.id shouldBe "sink"
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

    resolvedValidated shouldBe Invalid(NonEmptyList.of(UnknownFragment(id = "fragmentId", nodeId = "nodeFragmentId")))
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
          FragmentInputDefinition("start", List(FragmentParameter(ParameterName("ala"), FragmentClazzRef[String])))
        ),
        canonicalnode.SplitNode(
          Split("split"),
          List(
            List(
              FlatNode(Filter("filter2a", "false".spel)),
              FlatNode(BranchEndData(BranchEndDefinition("join2a", "join1")))
            ),
            List(
              FlatNode(Filter("filter2b", "false".spel)),
              FlatNode(BranchEndData(BranchEndDefinition("join2b", "join1")))
            )
          )
        )
      ),
      List(
        FlatNode(Join("join1", None, "union", Nil, Nil, None)),
        FlatNode(FragmentOutputDefinition("output", "output", Nil, None))
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
    resolvedValidated shouldBe Invalid(NonEmptyList.of(DuplicateFragmentOutputNamesInScenario("output1", "fragment")))

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
          FragmentNode(
            FragmentInput(
              id,
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

    def fragmentDisabledManyOutputs(
        id: String,
        fragmentId: String,
        params: List[(String, Expression)],
        outputs: Map[String, SubsequentNode]
    ): R =
      creator(
        FragmentNode(
          FragmentInput(
            id,
            FragmentRef(fragmentId, params.map { case (name, value) => NodeParameter(ParameterName(name), value) }),
            isDisabled = Some(true)
          ),
          outputs
        )
      )

    def fragmentDisabledEnd(id: String, fragmentId: String, params: (String, Expression)*): R =
      creator(
        FragmentNode(
          FragmentInput(
            id,
            FragmentRef(
              fragmentId,
              params.map { case (name, value) => NodeParameter(ParameterName(name), value) }.toList
            ),
            isDisabled = Some(true)
          ),
          Map()
        )
      )

    override def build(inner: Creator[R]): GraphBuilder[R] = builder.build(inner)

    override def creator: Creator[R] = builder.creator
  }

}
