package pl.touk.nussknacker.engine.spel.ast

import org.springframework.expression.spel.SpelNode
import pl.touk.nussknacker.engine.expression.IndexBasedTextRange

object SpelAst {

  // Node identifier in expression. Is it ok? Or mayby we should add some extra info like class?
  type SpelNodeId = IndexBasedTextRange

  object SpelNodeId {

    def apply(node: SpelNode): SpelNodeId =
      node.textRange
  }

  implicit class RichSpelNode(n: SpelNode) {

    def children: List[SpelNode] = {
      (0 until n.getChildCount).map(i => n.getChild(i))
    }.toList

    def textRange: IndexBasedTextRange =
      IndexBasedTextRange(n.getStartPosition, n.getEndPosition)

  }

}
