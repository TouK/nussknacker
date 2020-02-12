package pl.touk.nussknacker.engine.expression

import java.util.Optional

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.expression.ValueWithLazyContext
import pl.touk.nussknacker.engine.api.lazyy.LazyValuesProvider
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypingResult}
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Flavour

import scala.concurrent.Future

object NullExpression {
  def apply(original: String, expectedReturnType: TypingResult, flavour: Flavour): NullExpression =
    new NullExpression(original, returnedValue(expectedReturnType), flavour)

  def returnedValue(expectedReturnType: TypingResult): Any =
    expectedReturnType match {
      case t: TypedClass if t.klass == classOf[scala.Option[_]] => Option.empty
      case t: TypedClass if t.klass == classOf[java.util.Optional[_]] => Optional.empty()
      case _ => null
    }
}

case class NullExpression(original: String,
                          returnedValue: Any,
                          flavour: Flavour) extends api.expression.Expression with LazyLogging {
  override def language: String = flavour.languageId

  override def evaluate[T](ctx: Context, lazyValuesProvider: LazyValuesProvider): Future[ValueWithLazyContext[T]]
  = Future.successful(ValueWithLazyContext(returnedValue.asInstanceOf[T], ctx.lazyContext))
}
