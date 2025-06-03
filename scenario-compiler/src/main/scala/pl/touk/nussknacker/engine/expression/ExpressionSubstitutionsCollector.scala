package pl.touk.nussknacker.engine.expression

import pl.touk.nussknacker.engine.graph.expression.Expression

trait ExpressionSubstitutionsCollector {

  def collectSubstitutions(expression: Expression): List[ExpressionSubstitution]

}

case class ExpressionSubstitution(textRange: IndexBasedTextRange, replacement: String)
