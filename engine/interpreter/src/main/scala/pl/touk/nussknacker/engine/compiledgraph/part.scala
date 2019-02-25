package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.compile.ValidationContext
import pl.touk.nussknacker.engine.definition.CustomNodeInvoker
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.splittedgraph.end.{End, NormalEnd}
import pl.touk.nussknacker.engine.splittedgraph.splittednode
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode

object part {

  sealed trait ProcessPart {
    type T <: NodeData
    def node: SplittedNode[T]
    def validationContext: ValidationContext
    def id: String = node.id
  }

  sealed trait StartPart extends ProcessPart {
    def nextParts: List[SubsequentPart]
  }

  case class JoinPart(customNodeInvoker: CustomNodeInvoker[_],
                              node: splittednode.SourceNode[Join], validationContext: ValidationContext, nextValidationContext: ValidationContext,
                              nextParts: List[SubsequentPart], ends: List[End]) extends StartPart {
    override type T = Join

  }

  case class SourcePart(obj: api.process.Source[Any], node: splittednode.SourceNode[SourceNodeData], validationContext: ValidationContext,
                        nextParts: List[SubsequentPart], ends: List[End]) extends StartPart {
    override type T = SourceNodeData
  }

  sealed trait SubsequentPart extends ProcessPart

  case class CustomNodePart(customNodeInvoker: CustomNodeInvoker[_],
                            node: splittednode.OneOutputSubsequentNode[CustomNode], validationContext: ValidationContext, nextValidationContext: ValidationContext,
                            nextParts: List[SubsequentPart], ends: List[End]) extends SubsequentPart {
    override type T = CustomNode

  }

  case class SinkPart(obj: api.process.Sink, node: splittednode.EndingNode[Sink], validationContext: ValidationContext) extends SubsequentPart {
    override type T = Sink

    val ends = List(NormalEnd(node.id))
  }

  case class NextWithParts(next: splittednode.Next, nextParts: List[SubsequentPart], ends: List[End])

}