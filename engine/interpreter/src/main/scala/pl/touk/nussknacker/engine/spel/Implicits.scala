package pl.touk.nussknacker.engine.spel

import pl.touk.nussknacker.engine.graph.expression.Expression

import scala.language.implicitConversions

object Implicits {

  implicit def asSpelExpression(expression: String): Expression =
    Expression(
      language = SpelExpressionParser.languageId,
      expression = expression
    )

}