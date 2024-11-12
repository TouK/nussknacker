package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.api.CompiledExpression
import pl.touk.nussknacker.engine.compiledgraph.service.ServiceRef
import pl.touk.nussknacker.engine.compiledgraph.variable.Field
import pl.touk.nussknacker.engine.expression.parse.TypedExpression
import pl.touk.nussknacker.engine.graph.node.BranchEndDefinition

object node {

  sealed trait Node {
    def id: String
  }

  case class Source(id: String, ref: Option[String], next: Next) extends Node

  case class Sink(id: String, ref: String, isDisabled: Boolean) extends Node

  case class BranchEnd(definition: BranchEndDefinition) extends Node {
    override def id: String = definition.artificialNodeId
  }

  case class VariableBuilder(id: String, varName: String, value: Either[CompiledExpression, List[Field]], next: Next)
      extends Node

  case class Processor(id: String, service: ServiceRef, next: Next, isDisabled: Boolean) extends Node

  case class EndingProcessor(id: String, service: ServiceRef, isDisabled: Boolean) extends Node

  case class Enricher(id: String, service: ServiceRef, output: String, next: Next) extends Node

  case class Filter(
      id: String,
      expression: CompiledExpression,
      nextTrue: Option[Next],
      nextFalse: Option[Next],
      isDisabled: Boolean
  ) extends Node

  case class Switch(
      id: String,
      expression: Option[(String, CompiledExpression)],
      nexts: List[Case],
      defaultNext: Option[Next]
  ) extends Node

  case class Case(expression: CompiledExpression, node: Next)

  case class CustomNode(id: String, ref: String, next: Next) extends Node

  case class EndingCustomNode(id: String, ref: String) extends Node

  case class FragmentOutput(id: String, fieldsWithExpression: Map[String, TypedExpression], isDisabled: Boolean)
      extends Node

  case class FragmentUsageStart(id: String, params: List[CompiledParameter], next: Next) extends Node

  case class FragmentUsageEnd(id: String, outputVarDefinition: Option[FragmentOutputVarDefinition], next: Next)
      extends Node

  case class FragmentOutputVarDefinition(name: String, fields: List[Field])

  case class SplitNode(id: String, nexts: List[Next]) extends Node

  sealed trait Next {
    def id: String
  }

  case class NextNode(node: Node) extends Next {
    def id = node.id
  }

  case class PartRef(id: String) extends Next

}
