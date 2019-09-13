package pl.touk.nussknacker.engine.spel

import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Standard

import scala.language.implicitConversions

object Implicits {

  implicit def asSpelExpression(expression: String): Expression =
    Expression(
      language = Standard.languageId,
      expression = expression
    )

}