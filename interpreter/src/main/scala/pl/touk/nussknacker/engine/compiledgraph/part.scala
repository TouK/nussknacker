package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.context.ValidationContext
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
    def ends: List[TypedEnd]
  }

  sealed trait PotentiallyStartPart extends ProcessPart {
    def nextParts: List[SubsequentPart]
  }

  case class SourcePart(obj: api.process.Source[Any], node: splittednode.SourceNode[SourceNodeData], validationContext: ValidationContext,
                        nextParts: List[SubsequentPart], ends: List[TypedEnd]) extends PotentiallyStartPart {
    override type T = SourceNodeData
  }

  sealed trait SubsequentPart extends ProcessPart {
    def contextBefore: ValidationContext
  }

  case class CustomNodePart(transformer: AnyRef,
                            node: splittednode.SplittedNode[CustomNodeData],
                            contextBefore: ValidationContext,
                            validationContext: ValidationContext,
                            nextParts: List[SubsequentPart], ends: List[TypedEnd]) extends PotentiallyStartPart with SubsequentPart {
    override type T = CustomNodeData

  }
  
  case class SinkPart(obj: api.process.Sink, node: splittednode.EndingNode[Sink],
                      contextBefore: ValidationContext,
                      validationContext: ValidationContext) extends SubsequentPart {
    override type T = Sink

    //TODO: expression?
    val ends = List(TypedEnd(NormalEnd(node.id), validationContext))
  }

  case class NextWithParts(next: splittednode.Next, nextParts: List[SubsequentPart], ends: List[TypedEnd])

  case class TypedEnd(end: End, validationContext: ValidationContext)

}