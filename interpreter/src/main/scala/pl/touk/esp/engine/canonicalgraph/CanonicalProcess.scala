package pl.touk.esp.engine.canonicalgraph

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.canonicalgraph.canonicalnode.CanonicalNode
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node.{Filter, NodeData, Switch}

sealed trait CanonicalTreeNode

case class CanonicalProcess(metaData: MetaData, exceptionHandlerRef: ExceptionHandlerRef, nodes: List[CanonicalNode]) extends CanonicalTreeNode

// This package can't be inside marshall package because of format derivation for types using shapelesss
// Don't change class names - they are putted in the field 'type'
object canonicalnode {

  sealed trait CanonicalNode extends CanonicalTreeNode {
    def data: NodeData
    def id: String = data.id
  }

  case class FlatNode(data: NodeData) extends CanonicalNode

  case class FilterNode(data: Filter, nextFalse: List[CanonicalNode]) extends CanonicalNode

  case class SwitchNode(data: Switch, nexts: List[Case], defaultNext: List[CanonicalNode]) extends CanonicalNode

  case class Case(expression: Expression, nodes: List[CanonicalNode]) extends CanonicalTreeNode

}