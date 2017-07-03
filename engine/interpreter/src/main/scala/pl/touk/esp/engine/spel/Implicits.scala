package pl.touk.esp.engine.spel

import pl.touk.esp.engine.graph.expression.Expression

import scala.language.implicitConversions

object Implicits {

  implicit def asSpelExpression(expression: String): Expression =
    Expression(
      language = SpelExpressionParser.languageId,
      expression = expression
    )

}