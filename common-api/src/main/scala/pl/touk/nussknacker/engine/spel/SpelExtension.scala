package pl.touk.nussknacker.engine.spel

import pl.touk.nussknacker.engine.graph.expression.Expression

trait SpelExtension {

  implicit class SpelExpresion(expression: String) {
    def spel: Expression = Expression.spel(expression)

    def spelTemplate: Expression = Expression.spelTemplate(expression)

    def jsonTemplate: Expression = Expression.jsonTemplate(expression)

    def jsonExpression: Expression = Expression.json(expression)
  }

}

object SpelExtension extends SpelExtension
