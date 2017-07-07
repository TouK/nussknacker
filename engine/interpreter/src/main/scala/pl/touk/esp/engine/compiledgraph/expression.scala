package pl.touk.esp.engine.compiledgraph

import cats.data.Validated
import pl.touk.esp.engine.api.lazyy.{LazyContext, LazyValuesProvider}
import pl.touk.esp.engine.api.{Context, ValueWithContext}
import pl.touk.esp.engine.compile.ValidationContext
import pl.touk.esp.engine.compiledgraph

import scala.concurrent.Future
import scala.language.implicitConversions

object expression {

  trait Expression {
    def original: String

    def evaluate[T](ctx: Context, lazyValuesProvider: LazyValuesProvider): Future[ValueWithLazyContext[T]]
  }

  trait ExpressionParser {

    def languageId: String

    def parse(original: String, ctx: ValidationContext): Validated[ExpressionParseError, compiledgraph.expression.Expression]

    def parseWithoutContextValidation(original: String): Validated[ExpressionParseError, compiledgraph.expression.Expression]

  }

  case class ExpressionParseError(message: String)

  case class ValueWithLazyContext[T](value: T, lazyContext: LazyContext)

}