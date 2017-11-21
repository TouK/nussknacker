package pl.touk.nussknacker.engine.compiledgraph

import cats.data.{NonEmptyList, Validated}
import pl.touk.nussknacker.engine.api.lazyy.{LazyContext, LazyValuesProvider}
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.compile.ValidationContext
import pl.touk.nussknacker.engine.compiledgraph
import pl.touk.nussknacker.engine.compiledgraph.typing.TypingResult
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ClazzRef

import scala.concurrent.Future
import scala.language.implicitConversions

object expression {

  trait Expression {
    def original: String

    def evaluate[T](ctx: Context, lazyValuesProvider: LazyValuesProvider): Future[ValueWithLazyContext[T]]
  }

  trait ExpressionParser {

    def languageId: String

    def parse(original: String, ctx: ValidationContext, expectedType: ClazzRef):
      Validated[NonEmptyList[ExpressionParseError], (TypingResult, compiledgraph.expression.Expression)]

    def parseWithoutContextValidation(original: String, expectedType: ClazzRef): Validated[ExpressionParseError, compiledgraph.expression.Expression]

  }

  case class ExpressionParseError(message: String)

  case class ValueWithLazyContext[T](value: T, lazyContext: LazyContext)

}