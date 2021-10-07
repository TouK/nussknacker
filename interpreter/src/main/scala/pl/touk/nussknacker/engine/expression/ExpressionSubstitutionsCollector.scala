package pl.touk.nussknacker.engine.expression

import pl.touk.nussknacker.engine.api.graph.expression.Expression

trait ExpressionSubstitutionsCollector {

  def collectSubstitutions(expression: Expression): List[ExpressionSubstitution]

}

case class ExpressionSubstitution(position: PositionRange, replacement: String)


case class PositionRange(start: Int, end: Int)
