package pl.touk.nussknacker.engine.compiledgraph

import cats.data.Validated
import pl.touk.nussknacker.engine.api.lazyy.{LazyContext, LazyValuesProvider}
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.compile.ValidationContext
import pl.touk.nussknacker.engine.compiledgraph

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