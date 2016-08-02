package pl.touk.esp.engine.compiledgraph

import cats.data.Validated
import pl.touk.esp.engine.api.Context
import pl.touk.esp.engine.compiledgraph

import scala.language.implicitConversions

object expression {

  trait Expression {
    def original: String

    def evaluate[T](ctx: Context): T
  }

  trait ExpressionParser {

    def languageId: String

    def parse(original: String): Validated[ExpressionParseError, compiledgraph.expression.Expression]

  }

  case class ExpressionParseError(message: String)

}