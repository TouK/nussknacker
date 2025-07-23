package pl.touk.nussknacker.engine.spel.ast

import org.springframework.expression.spel.SpelNode
import pl.touk.nussknacker.engine.expression.IndexBasedTextRange

object SpelAst {

  // Node identifier in expression. Is it ok? Or mayby we should add some extra info like class?
  type SpelNodeId = IndexBasedTextRange

  implicit class RichSpelNode(n: SpelNode) {

    def children: List[SpelNode] = {
      (0 until n.getChildCount).map(i => n.getChild(i))
    }.toList

    // When we parse template expression, SpelNode has text positions local to nested spel expression
    // We often have to adjust this text range to have position global for the whole expression
    // Another approach would be to rewrite SpelParser - see NuSpelExpressionParser.doParseExpression
    def adjustedTextRange(spelExpressionTextRange: IndexBasedTextRange): IndexBasedTextRange =
      textRange.shiftRight(spelExpressionTextRange.start)

    def textRange: IndexBasedTextRange =
      IndexBasedTextRange(n.getStartPosition, n.getEndPosition)

  }

}
