package pl.touk.nussknacker.engine.canonicalgraph

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, Case, FilterNode, FlatNode, SplitNode, Subprocess, SwitchNode}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef

import scala.language.implicitConversions

class CanonicalProcessTest extends AnyFunSuite with Matchers {

  val source1 = FlatNode(Source("in", SourceRef("sourceType", Nil)))

  val sink1 = FlatNode(Sink("out", SinkRef("sinkType", Nil)))

  val disabledFilter1 = FilterNode(data = Filter("filter1", "''", isDisabled = Some(true)), List(sink1))

  test("#withoutDisabledNodes when all nodes are enabled") {
    val withNodesEnabled = process(
      List(source1, subprocess(List(sink1), isDisabled = false)))

    withNodesEnabled.withoutDisabledNodes shouldBe withNodesEnabled
  }

  test("#withoutDisabledNodes with disabled fragment") {
    val withDisabledSubprocess = process(
      List(source1, subprocess(List(sink1), isDisabled = true)))

    withDisabledSubprocess.withoutDisabledNodes shouldBe process(List(source1, sink1))
  }

  test("#withoutDisabledNodes with fragment with disabled fragment") {
    val withSubprocessWithDisabledSubprocess = process(
      List(
        source1,
        subprocess(
          output = List(
            subprocess(
              output = List(sink1),
              isDisabled = true
            )
          ),
          isDisabled = false
        )
      )
    )

    withSubprocessWithDisabledSubprocess.withoutDisabledNodes shouldBe process(
      List(
        source1,
        subprocess(
          output = List(sink1),
          isDisabled = false
        )
      )
    )
  }

  test("#withoutDisabledNodes with disabled fragment with disabled fragment") {
    val withDisabledSubprocessWithDisabledSubprocess = process(
      List(
        source1,
        subprocess(
          output = List(
            subprocess(
              output = List(sink1),
              isDisabled = true
            )
          ),
          isDisabled = true
        )
      )
    )

    withDisabledSubprocessWithDisabledSubprocess.withoutDisabledNodes shouldBe process(
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
          data = Switch("switch1"),
          nexts = List(
            Case("''", List(disabledFilter1)),
            Case("''", List(sink1))
          ),
          defaultNext = List(disabledFilter1))
      )
    ).withoutDisabledNodes shouldBe process(
      List(
        source1,
        SwitchNode(
          data = Switch("switch1"),
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
          data = Split("split1"),
          nexts = List(
            List(
              subprocess(
                output = List(
                  subprocess(
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
          data = Split("split1"),
          nexts = List(
            List(
              subprocess(
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

  private def subprocess(output: List[CanonicalNode], isDisabled: Boolean) =
    Subprocess(
      SubprocessInput(
        "sub1",
        SubprocessRef("sub1", Nil),
        isDisabled = Some(isDisabled)
      ),
      Map("subOut" -> output)
    )

  private def process(nodes: List[CanonicalNode]) =
    CanonicalProcess(
      MetaData("process1", StreamMetaData()),
      nodes = nodes,
      additionalBranches = List.empty)

  private implicit def asSampleExpression(expression: String): Expression =
    Expression(
      language = "sample",
      expression = expression
    )

}
