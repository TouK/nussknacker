package pl.touk.nussknacker.engine.expression

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.expression.ValueWithLazyContext
import pl.touk.nussknacker.engine.api.lazyy.LazyValuesProvider
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Flavour

import scala.concurrent.Future

case class NullExpression(original: String,
                          flavour: Flavour) extends api.expression.Expression with LazyLogging {
  override def language: String = flavour.languageId

  override def evaluate[T](ctx: Context, globals: Map[String, Any], lazyValuesProvider: LazyValuesProvider): ValueWithLazyContext[T]
  = ValueWithLazyContext(null.asInstanceOf[T], ctx.lazyContext)
}
