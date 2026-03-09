package pl.touk.nussknacker.engine.compiledgraph

import pl.touk.nussknacker.engine.compiledgraph.service.ServiceRef
import pl.touk.nussknacker.engine.compiledgraph.variable.Field
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, TypedExpression}
import pl.touk.nussknacker.engine.graph.node.BranchEndDefinition

object node {

  sealed trait Node {
    def id: String
    def name: String
  }

  case class Source(id: String, name: String, ref: Option[String], next: Option[Next]) extends Node

  case class Sink(id: String, name: String, ref: String, isDisabled: Boolean) extends Node

  case class BranchEnd(definition: BranchEndDefinition) extends Node {
    override def id: String   = definition.artificialNodeId
    override def name: String = definition.artificialNodeId
  }

  case class VariableBuilder(
      id: String,
      name: String,
      varName: String,
      value: Either[CompiledExpression, List[Field]],
      next: Option[Next]
  ) extends Node

  case class Processor(id: String, name: String, service: ServiceRef, next: Option[Next], isDisabled: Boolean)
      extends Node

  case class EndingProcessor(id: String, name: String, service: ServiceRef, isDisabled: Boolean) extends Node

  case class Enricher(
      id: String,
      name: String,
      service: ServiceRef,
      output: String,
      next: Option[Next],
      mockedOutput: Option[CompiledExpression]
  ) extends Node

  case class Filter(
      id: String,
      name: String,
      expression: CompiledExpression,
      nextTrue: Option[Next],
      nextFalse: Option[Next],
      isDisabled: Boolean
  ) extends Node

  case class Switch(
      id: String,
      name: String,
      expression: Option[(String, CompiledExpression)],
      nexts: List[Case],
      defaultNext: Option[Next]
  ) extends Node

  case class Case(expression: CompiledExpression, next: Option[Next])

  case class CustomNode(id: String, name: String, ref: String, next: Option[Next]) extends Node

  case class EndingCustomNode(id: String, name: String, ref: String) extends Node

  case class FragmentOutput(
      id: String,
      name: String,
      fieldsWithExpression: Map[String, TypedExpression],
      isDisabled: Boolean
  ) extends Node

  case class FragmentUsageStart(id: String, name: String, params: List[CompiledParameter], next: Option[Next])
      extends Node

  case class FragmentUsageEnd(
      id: String,
      name: String,
      fragmentUsageStartNodeId: String,
      outputVarDefinition: Option[FragmentOutputVarDefinition],
      next: Option[Next]
  ) extends Node

  case class FragmentOutputVarDefinition(name: String, fields: List[Field])

  case class SplitNode(id: String, name: String, nexts: List[Next]) extends Node

  sealed trait Next {
    def id: String
  }

  case class NextNode(node: Node) extends Next {
    def id = node.id
  }

  case class PartRef(id: String) extends Next

}
