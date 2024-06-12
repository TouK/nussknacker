package pl.touk.nussknacker.engine.spel

import pl.touk.nussknacker.engine.graph.expression.Expression

import scala.language.implicitConversions

// TODO: Should be replaced with: pl.touk.nussknacker.engine.spel.SpelExtension
object Implicits {

  implicit def asSpelExpression(expression: String): Expression = Expression.spel(expression)

}
