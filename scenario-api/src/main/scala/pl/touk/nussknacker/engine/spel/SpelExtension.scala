package pl.touk.nussknacker.engine.spel

import pl.touk.nussknacker.engine.graph.expression.Expression

trait SpelExtension {

  implicit class SpelExpresion(expression: String) {
    def spel: Expression = Expression.spel(expression)
  }

}

object SpelExtension extends SpelExtension
