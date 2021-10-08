package pl.touk.nussknacker.engine.expression

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Flavour

case class NullExpression(original: String,
                          flavour: Flavour) extends api.expression.Expression with LazyLogging {
  override def language: String = flavour.languageId

  override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = null.asInstanceOf[T]
}
