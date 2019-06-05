package pl.touk.nussknacker.engine.canonicalgraph

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.CanonicalNode
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._

sealed trait CanonicalTreeNode

object CanonicalProcess {

  def withoutDisabledNodes(process: CanonicalProcess): CanonicalProcess = {
    def withoutDisabled(nodes: List[CanonicalNode]): List[CanonicalNode] = {
      nodes.filter {
        _.data match {
          case nodeData: Disableable if nodeData.isDisabled.contains(true) => false
          case _ => true
        }
      }.map {
        case flatNode: canonicalnode.FlatNode =>
          flatNode

        case filter: canonicalnode.FilterNode =>
          filter.copy(nextFalse = withoutDisabled(filter.nextFalse))

        case switch: canonicalnode.SwitchNode =>
          switch.copy(
            nexts = switch.nexts.map { caseNode =>
              caseNode.copy(nodes = withoutDisabled(caseNode.nodes))
            }.filterNot(_.nodes.isEmpty),
            defaultNext = withoutDisabled(switch.defaultNext))

        case split: canonicalnode.SplitNode =>
          split.copy(nexts = split.nexts.map(withoutDisabled).filterNot(_.isEmpty))

        case subprocess: canonicalnode.Subprocess =>
          subprocess.copy(
            outputs = subprocess.outputs.map { case (id, canonicalNodes) =>
              (id, withoutDisabled(canonicalNodes))
            }.filterNot { case (_, canonicalNodes) => canonicalNodes.isEmpty }
          )
      }
    }

    process.copy(nodes = withoutDisabled(process.nodes))
  }
}

//in fact with branches/join this form is not canonical anymore - graph can be represented in more than way
case class CanonicalProcess(metaData: MetaData,
                           //TODO: this makes sense only for StreamProcess, it should be moved to StreamMetadata
                           //not so easy to do, as it has classes from interprete and StreamMetadata is in API
                            exceptionHandlerRef: ExceptionHandlerRef,
                            nodes: List[CanonicalNode],
                           //Separation from nodes and Option is for json backwards compatibility
                           //in the future this form will probably be removed
                            additionalBranches: Option[List[List[CanonicalNode]]]
                           ) extends CanonicalTreeNode {

  lazy val withoutDisabledNodes: CanonicalProcess = CanonicalProcess.withoutDisabledNodes(this)
}

object canonicalnode {

  sealed trait CanonicalNode extends CanonicalTreeNode {
    def data: NodeData
    def id: String = data.id
  }

  case class FlatNode(data: NodeData) extends CanonicalNode

  case class FilterNode(data: Filter, nextFalse: List[CanonicalNode]) extends CanonicalNode

  case class SwitchNode(data: Switch, nexts: List[Case], defaultNext: List[CanonicalNode]) extends CanonicalNode

  case class SplitNode(data: Split, nexts: List[List[CanonicalNode]]) extends CanonicalNode

  case class Case(expression: Expression, nodes: List[CanonicalNode]) extends CanonicalTreeNode

  case class Subprocess(data: SubprocessInput,
                        outputs: Map[String, List[CanonicalNode]]) extends CanonicalNode

}