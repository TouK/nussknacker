package pl.touk.nussknacker.engine.expression

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Flavour

case class NullExpression(original: String, flavour: Flavour) extends CompiledExpression {
  override def language: String = flavour.languageId

  override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = null.asInstanceOf[T]
}
