package pl.touk.nussknacker.engine.language.json

import cats.data.{Validated, ValidatedNel}
import io.circe.parser
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.json.decoders.FromJsonTypingResultBasedDecoder
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, ExpressionParser, TypedExpression}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.language.json.JsonTemplateParser._
import pl.touk.nussknacker.engine.spel.SpelExpressionParser

class JsonTemplateParser(spelTemplateParser: SpelExpressionParser, spelParser: SpelExpressionParser)
    extends ExpressionParser {

  private val typeDeterminer = new JsonTemplateTypeDeterminer(spelParser)

  override def languageId: Expression.Language = Expression.Language.JsonTemplate

  override def parse(
      originalJsonString: String,
      validationContext: ValidationContext,
      expectedType: typing.TypingResult
  ): ValidatedNel[ExpressionParseError, TypedExpression] = {
    def validateExpectedType(parsed: TypedExpression) = {
      Validated.condNel(
        parsed.typingInfo.typingResult.canBeLooselyAssignedTo(expectedType),
        parsed,
        JsonTemplateExpressionTypeError(expectedType, parsed.typingInfo.typingResult)
      )
    }

    spelTemplateParser
      .parse(originalJsonString, validationContext, Typed[String])
      .map(_.expression)
      .andThen { spelTemplateExpression =>
        typeDeterminer
          .expressionResultType(spelTemplateExpression, validationContext)
          .map(toJsonTemplateExpression(originalJsonString, spelTemplateExpression, _))
      }
      .andThen(validateExpectedType)
  }

  override def parseWithoutContextValidation(
      originalJsonString: String,
      expectedType: typing.TypingResult
  ): ValidatedNel[ExpressionParseError, CompiledExpression] = {
    spelTemplateParser
      .parseWithoutContextValidation(originalJsonString, Typed[String])
      .map(toJsonTemplateExpression(originalJsonString, _, expectedType))
      .map(_.expression)
  }

  private def toJsonTemplateExpression(
      originalJsonString: String,
      spelTemplateExpression: CompiledExpression,
      expressionResultType: TypingResult
  ) = {
    TypedExpression(
      new CompiledJsonTemplateExpression(
        originalJsonString,
        spelTemplateExpression,
        expressionResultType
      ),
      ExpressionTypingInfo(expressionResultType)
    )
  }

}

object JsonTemplateParser {

  private case class JsonTemplateExpressionTypeError(
      expected: TypingResult,
      found: TypingResult
  ) extends ExpressionParseError {
    override def message: String = s"Bad expression type, expected: ${expected.display}, found: ${found.display}"
  }

  private class CompiledJsonTemplateExpression(
      originalJsonString: String,
      templateCompiledExpression: CompiledExpression,
      typ: typing.TypingResult
  ) extends CompiledExpression {

    override def language: Language = Expression.Language.JsonTemplate

    override def original: String = originalJsonString

    override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = {
      val renderedTemplate = templateCompiledExpression.evaluate[String](ctx, globals)
      parser
        .parse(renderedTemplate)
        .flatMap { value =>
          FromJsonTypingResultBasedDecoder.decodeValue(typ, value.hcursor)
        }
        .fold(e => throw new JsonTemplateDecodingException(renderedTemplate, e), _.asInstanceOf[T])
    }

  }

  private class JsonTemplateDecodingException(
      renderedTemplate: String,
      cause: Throwable,
  ) extends NonTransientException(
        input = renderedTemplate,
        message = s"Rendered template [$renderedTemplate] cannot be decoded ad json, message: ${cause.getMessage}",
        cause = cause
      )

}
