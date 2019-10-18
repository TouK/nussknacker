package pl.touk.nussknacker.engine.spel.ast

import org.springframework.expression.spel.SpelNode

object SpelAst {

  case class PositionRange(start: Int, end: Int)

  object PositionRange {

    def apply(node: SpelNode): PositionRange =
      PositionRange(node.getStartPosition, node.getEndPosition)

  }

  // Node identifier in expression. Is it ok? Or mayby we should add some extra info like class?
  type SpelNodeId = PositionRange

  object SpelNodeId {

    def apply(node: SpelNode): SpelNodeId =
      PositionRange(node)

  }

  implicit class RichSpelNode(n: SpelNode) {

    def children: List[SpelNode] = {
      (0 until n.getChildCount).map(i => n.getChild(i))
      }.toList

    def childrenHead: SpelNode = {
      n.getChild(0)
    }

  }

}
