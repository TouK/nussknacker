package pl.touk.nussknacker.extensions

import pl.touk.nussknacker.engine.graph.expression.Expression

trait SpelExtension {

  implicit class SpelExpresion(expression: String) {
    def spel(): Expression = Expression.spel(expression)
  }

}
