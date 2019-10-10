package pl.touk.nussknacker.engine.spel.ast

import org.springframework.expression.spel.SpelNode
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

class SpelSubstitutionsCollector(typeForNode: SpelNode => Option[TypingResult],
                                 replacementPF: PartialFunction[OptionallyTypedNode, String]) {

  def collectSubstitutions(node: SpelNode): List[ExpressionSubstitution] = {
    val forSelf = replacementPF.lift(OptionallyTypedNode(node, typeForNode(node)))
      .map { replacement =>
        val (startPosition, endPosition) = positionRange(node)
        ExpressionSubstitution(startPosition, endPosition, replacement)
      }
    val forChildren = getChildren(node).flatMap(n => collectSubstitutions(n))
    forSelf.toList ::: forChildren
  }

  private def positionRange(node: SpelNode): (Int, Int) = {
    val children = getChildren(node).map(positionRange)
    val start = if (children.isEmpty) node.getStartPosition else Math.min(node.getStartPosition, children.map(_._1).min)
    val end = if (children.isEmpty) node.getEndPosition else Math.max(node.getEndPosition, children.map(_._2).max)
    (start, end)
  }

  private def getChildren(node: SpelNode): List[SpelNode] =
    0.until(node.getChildCount).map(i => node.getChild(i)).toList

}

case class OptionallyTypedNode(node: SpelNode, optionalType: Option[TypingResult])

object SpelNodeWithChildren {

  def unapply(node: SpelNode): Option[(SpelNode, List[SpelNode])] = Some((node, 0.until(node.getChildCount).map(node.getChild).toList))

}

case class ExpressionSubstitution(startPosition: Int, endPosition: Int, replacement: String)