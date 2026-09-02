package pl.touk.nussknacker.engine.compiledgraph

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.component.ComponentOutput
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

  case class SourcePart(
      obj: api.process.Source,
      node: splittednode.SourceNode[SourceNodeData],
      validationContext: ValidationContext,
      nextParts: List[SubsequentPart],
      ends: List[TypedEnd]
  ) extends PotentiallyStartPart {
    override type T = SourceNodeData
  }

  sealed trait SubsequentPart extends ProcessPart {
    def contextBefore: ValidationContext
  }

  /**
    * `outputs` is built from the component declaration, so the head is always the main output (in the named-wiring
    * shape a `DeadEnd` stands in when it is unwired; an ending node ends with a `NormalEnd` instead) and the wired
    * additional outputs follow in declaration order.
    */
  case class CustomNodePart(
      transformer: AnyRef,
      contextBefore: ValidationContext,
      validationContext: ValidationContext,
      outputs: NonEmptyList[CompiledOutput]
  ) extends PotentiallyStartPart
      with SubsequentPart {

    override type T = CustomNodeData

    override def node: splittednode.SplittedNode[CustomNodeData] =
      outputs.head.node

    override def nextParts: List[SubsequentPart] =
      outputs.toList.flatMap(_.nextParts)

    override def ends: List[TypedEnd] =
      outputs.toList.flatMap(_.ends)
  }

  case class CompiledOutput(
      output: ComponentOutput,
      node: splittednode.SplittedNode[CustomNodeData],
      nextParts: List[SubsequentPart],
      ends: List[TypedEnd]
  )

  case class SinkPart(
      obj: api.process.Sink,
      node: splittednode.EndingNode[Sink],
      contextBefore: ValidationContext,
      validationContext: ValidationContext
  ) extends SubsequentPart {
    override type T = Sink

    // TODO: expression?
    val ends = List(TypedEnd(NormalEnd(node.id), validationContext))
  }

  case class TypedEnd(end: End, validationContext: ValidationContext)

}
