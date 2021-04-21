package pl.touk.nussknacker.engine.canonicalgraph

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, Case, FilterNode, FlatNode, SplitNode, Subprocess, SwitchNode}
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.{Filter, Sink, Source, Split, SubprocessInput, Switch}
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef

class CanonicalProcessTest extends FunSuite with Matchers {
  import pl.touk.nussknacker.engine.spel.Implicits.asSpelExpression

  val source1 = FlatNode(Source("in", SourceRef("sourceType", Nil)))

  val sink1 = FlatNode(Sink("out", SinkRef("sinkType", Nil)))

  val disabledFilter1 = FilterNode(data = Filter("filter1", "''", isDisabled = Some(true)), List(sink1))

  def subprocess(output: List[CanonicalNode], isDisabled: Boolean) =
    Subprocess(
      SubprocessInput(
        "sub1",
        SubprocessRef("sub1", Nil),
        isDisabled = Some(isDisabled)
      ),
      Map("subOut" -> output)
  )

  def process(nodes: List[CanonicalNode]) =
    CanonicalProcess(
      MetaData("process1", StreamMetaData()),
      ExceptionHandlerRef(Nil),
      nodes = nodes,
      additionalBranches = None)

  test("#withoutDisabledNodes when all nodes are enabled") {
    val withNodesEnabled = process(
      List(source1, subprocess(List(sink1), isDisabled = false)))

    withNodesEnabled.withoutDisabledNodes shouldBe withNodesEnabled
  }

  test("#withoutDisabledNodes with disabled subprocess") {
    val withDisabledSubprocess = process(
      List(source1, subprocess(List(sink1), isDisabled = true)))

    withDisabledSubprocess.withoutDisabledNodes shouldBe process(List(source1, sink1))
  }

  test("#withoutDisabledNodes with subprocess with disabled subprocess") {
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

  test("#withoutDisabledNodes with disabled subprocess with disabled subprocess") {
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
          data = Switch("switch1", "''", ""),
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
          data = Switch("switch1", "''", ""),
          nexts = List(
            Case("''", List(sink1))
          ),
          defaultNext = List.empty
        )
      )
    )
  }

  test("#withoutDisabledNodes with split with subprocess with disabled subprocess") {
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
}
