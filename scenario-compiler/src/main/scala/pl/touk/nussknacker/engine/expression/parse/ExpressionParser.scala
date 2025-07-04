package pl.touk.nussknacker.engine.expression.parse

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

trait ExpressionParser {

  def languageId: Language

  def parse(
      original: String,
      ctx: ValidationContext,
      expectedType: TypingResult
  ): ValidatedNel[ExpressionParseError, TypedExpression]

  // This method should only provide an evaluable expression - no types validation should be done here. It is used
  // in runtime, where we already validated all expressions using the normal "parse" method
  def parseWithoutContextValidation(
      original: String,
      expectedType: TypingResult
  ): ValidatedNel[ExpressionParseError, CompiledExpression]

}
