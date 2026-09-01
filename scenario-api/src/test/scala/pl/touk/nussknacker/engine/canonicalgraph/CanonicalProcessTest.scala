package pl.touk.nussknacker.engine.canonicalgraph

import cats.data.NonEmptyList
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, NodeName, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{
  CanonicalNode,
  Case,
  CustomNodeWithOutputs,
  FilterNode,
  FlatNode,
  Fragment,
  Output,
  SplitNode,
  SwitchNode
}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef

import scala.language.implicitConversions

class CanonicalProcessTest extends AnyFunSuite with Matchers {

  val source1 = FlatNode(Source(NodeId("in"), NodeName("in"), SourceRef("sourceType", Nil)))

  val sink1 = FlatNode(Sink(NodeId("out"), NodeName("out"), SinkRef("sinkType", Nil)))

  val disabledFilter1 =
    FilterNode(data = Filter(NodeId("filter1"), NodeName("filter1"), "''", isDisabled = Some(true)), List(sink1))

  val customNode1 =
    CustomNode(NodeId("custom1"), NodeName("custom1"), outputVar = None, nodeType = "dedup", parameters = List.empty)

  test("#withoutDisabledNodes when all nodes are enabled") {
    val withNodesEnabled = process(List(source1, fragment(List(sink1), isDisabled = false)))

    withNodesEnabled.withoutDisabledNodes shouldBe withNodesEnabled
  }

  test("#withoutDisabledNodes with disabled fragment") {
    val withDisabledFragment = process(List(source1, fragment(List(sink1), isDisabled = true)))

    withDisabledFragment.withoutDisabledNodes shouldBe process(List(source1, sink1))
  }

  test("#withoutDisabledNodes with fragment with disabled fragment") {
    val withFragmentWithDisabledFragment = process(
      List(
        source1,
        fragment(
          output = List(
            fragment(
              output = List(sink1),
              isDisabled = true
            )
          ),
          isDisabled = false
        )
      )
    )

    withFragmentWithDisabledFragment.withoutDisabledNodes shouldBe process(
      List(
        source1,
        fragment(
          output = List(sink1),
          isDisabled = false
        )
      )
    )
  }

  test("#withoutDisabledNodes with disabled fragment with disabled fragment") {
    val withDisabledFragmentWithDisabledFragment = process(
      List(
        source1,
        fragment(
          output = List(
            fragment(
              output = List(sink1),
              isDisabled = true
            )
          ),
          isDisabled = true
        )
      )
    )

    withDisabledFragmentWithDisabledFragment.withoutDisabledNodes shouldBe process(
      List(source1, sink1)
    )
  }

  test("#withoutDisabledNodes with disabled filter") {
    process(
      List(
        source1,
        disabledFilter1,
        sink1
      )
    ).withoutDisabledNodes shouldBe process(
      List(source1, sink1)
    )
  }

  test("#withoutDisabledNodes with switch with disabled default filter") {
    process(
      List(
        source1,
        SwitchNode(
          data = Switch(NodeId("switch1"), NodeName("switch1"), None, None),
          nexts = List(
            Case("''", List(disabledFilter1)),
            Case("''", List(sink1))
          ),
          defaultNext = List(disabledFilter1)
        )
      )
    ).withoutDisabledNodes shouldBe process(
      List(
        source1,
        SwitchNode(
          data = Switch(NodeId("switch1"), NodeName("switch1"), None, None),
          nexts = List(
            Case("''", List(sink1))
          ),
          defaultNext = List.empty
        )
      )
    )
  }

  test("#withoutDisabledNodes with split with fragment with disabled fragment") {
    process(
      List(
        source1,
        SplitNode(
          data = Split(NodeId("split1"), NodeName("split1")),
          nexts = List(
            List(
              fragment(
                output = List(
                  fragment(
                    output = List(
                      sink1
                    ),
                    isDisabled = true
                  )
                ),
                isDisabled = false
              )
            ),
            List(sink1)
          )
        )
      )
    ).withoutDisabledNodes shouldBe process(
      List(
        source1,
        SplitNode(
          data = Split(NodeId("split1"), NodeName("split1")),
          nexts = List(
            List(
              fragment(
                output = List(sink1),
                isDisabled = false
              )
            ),
            List(sink1)
          )
        )
      )
    )
  }

  test("#withoutDisabledNodes with custom node with disabled node in output") {
    process(
      List(
        source1,
        CustomNodeWithOutputs(
          data = customNode1,
          outputs = List(Output("rejected", List(disabledFilter1, sink1)))
        )
      )
    ).withoutDisabledNodes shouldBe process(
      List(
        source1,
        CustomNodeWithOutputs(
          data = customNode1,
          outputs = List(Output("rejected", List(sink1)))
        )
      )
    )
  }

  test("#withoutDisabledNodes with custom node whose only output node is disabled collapses to a flat node") {
    process(
      List(
        source1,
        CustomNodeWithOutputs(
          data = customNode1,
          outputs = List(Output("rejected", List(disabledFilter1)))
        )
      )
    ).withoutDisabledNodes shouldBe process(
      List(
        source1,
        FlatNode(customNode1)
      )
    )
  }

  test("CustomNodeWithOutputs built from a list falls back to a flat node when the list is empty") {
    CustomNodeWithOutputs(customNode1, List.empty) shouldBe FlatNode(customNode1)

    CustomNodeWithOutputs(customNode1, List(Output("rejected", List(sink1)))) shouldBe
      CustomNodeWithOutputs(customNode1, NonEmptyList.of(Output("rejected", List(sink1))))
  }

  private def fragment(output: List[CanonicalNode], isDisabled: Boolean) =
    Fragment(
      FragmentInput(
        NodeId("sub1"),
        NodeName("sub1"),
        FragmentRef("sub1", Nil, Map.empty),
        isDisabled = Some(isDisabled)
      ),
      Map("subOut" -> output)
    )

  private def process(nodes: List[CanonicalNode]) =
    CanonicalProcess(MetaData("process1", StreamMetaData()), nodes = nodes, additionalBranches = List.empty)

  private implicit def asSampleExpression(expression: String): Expression =
    Expression(
      language = Language.Spel,
      expression = expression
    )

}
