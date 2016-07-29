package pl.touk.esp.engine.compiledgraph

import cats.data.Validated
import pl.touk.esp.engine.Interpreter.ContextImpl
import pl.touk.esp.engine.compiledgraph

import scala.language.implicitConversions

object expression {

  trait Expression {
    def original: String

    def evaluate[T](ctx: ContextImpl): T
  }

  trait ExpressionParser {

    def languageId: String

    def parse(original: String): Validated[ExpressionParseError, compiledgraph.expression.Expression]

  }

  case class ExpressionParseError(message: String)

}