package pl.touk.nussknacker.engine.spel.ast

import org.springframework.expression.spel.SpelNode
import pl.touk.nussknacker.engine.expression.PositionRange

object SpelAst {

  // Node identifier in expression. Is it ok? Or mayby we should add some extra info like class?
  type SpelNodeId = PositionRange

  object SpelNodeId {

    def apply(node: SpelNode): SpelNodeId =
      node.positionRange
  }

  implicit class RichSpelNode(n: SpelNode) {

    def children: List[SpelNode] = {
      (0 until n.getChildCount).map(i => n.getChild(i))
      }.toList

    def childrenHead: SpelNode = {
      n.getChild(0)
    }

    def positionRange: PositionRange =
      PositionRange(n.getStartPosition, n.getEndPosition)

  }

}
