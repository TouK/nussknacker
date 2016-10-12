package pl.touk.esp.engine.compiledgraph

import cats.data.Validated
import pl.touk.esp.engine.api.lazyy.LazyValuesProvider
import pl.touk.esp.engine.api.{Context, ValueWithModifiedContext}
import pl.touk.esp.engine.compile.ValidationContext
import pl.touk.esp.engine.compiledgraph

import scala.language.implicitConversions

object expression {

  trait Expression {
    def original: String

    def evaluate[T](ctx: Context, lazyValuesProvider: LazyValuesProvider): ValueWithModifiedContext[T]
  }

  trait ExpressionParser {

    def languageId: String

    def parse(original: String, ctx: ValidationContext): Validated[ExpressionParseError, compiledgraph.expression.Expression]

    def parseWithoutContextValidation(original: String): Validated[ExpressionParseError, compiledgraph.expression.Expression]

  }

  case class ExpressionParseError(message: String)

}