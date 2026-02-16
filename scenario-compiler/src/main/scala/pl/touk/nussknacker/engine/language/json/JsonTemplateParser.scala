package pl.touk.nussknacker.engine.language.json

import cats.data.ValidatedNel
import io.circe.parser
import pl.touk.nussknacker.engine.api.{Context, TemplateEvaluationResult}
import pl.touk.nussknacker.engine.api.TemplateRenderedPart.{RenderedLiteral, RenderedSubExpression}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.json.decoders.FromJsonTypingResultBasedDecoder
import pl.touk.nussknacker.engine.api.json.encoders.ToJsonEncoder
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.expression.ExpectedType
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
      expectedType: ExpectedType
  ): ValidatedNel[ExpressionParseError, TypedExpression] = {
    handleBlankExpressionAsNullExpression(originalJsonString).getOrElse {
      parseNonBlankExpression(originalJsonString, validationContext, expectedType)
    }
  }

  private def parseNonBlankExpression(
      originalJsonString: String,
      validationContext: ValidationContext,
      expectedType: ExpectedType
  ): ValidatedNel[ExpressionParseError, TypedExpression] = {
    spelTemplateParser
      .parse(
        originalJsonString,
        validationContext,
        ExpectedType(Typed[TemplateEvaluationResult], expectedType.strictTypeMatch)
      )
      .map(_.expression)
      .andThen { spelTemplateExpression =>
        typeDeterminer
          .expressionResultType(spelTemplateExpression, validationContext, expectedType)
          .map(toJsonTemplateExpression(originalJsonString, spelTemplateExpression, _))
      }
      .andThen { typedExpression =>
        ExpressionParser
          .validateResultTypeMatchExpectedType(typedExpression.typingInfo.typingResult, expectedType)
          .map(_ => typedExpression)
      }
  }

  private def toJsonTemplateExpression(
      originalJsonString: String,
      compiledSpelTemplateExpression: CompiledExpression,
      expressionResultType: TypingResult
  ) = {
    TypedExpression(
      new CompiledJsonTemplateExpression(originalJsonString, compiledSpelTemplateExpression, expressionResultType),
      ExpressionTypingInfo(expressionResultType)
    )
  }

}

object JsonTemplateParser {

  private class CompiledJsonTemplateExpression(
      originalJsonString: String,
      compiledSpelTemplateExpression: CompiledExpression,
      typ: TypingResult
  ) extends CompiledExpression {

    override def language: Language = Expression.Language.JsonTemplate

    override def original: String = originalJsonString

    override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = {
      val renderedTemplate = compiledSpelTemplateExpression
        .evaluate[TemplateEvaluationResult](ctx, globals)
        .renderedParts
        .map {
          case RenderedLiteral(value)       => value
          case RenderedSubExpression(value) => renderExpressionResult(value)
        }
        .mkString
      parser
        .parse(renderedTemplate)
        .flatMap { value =>
          FromJsonTypingResultBasedDecoder.decodeValue(typ, value.hcursor)
        }
        .fold(e => throw new JsonTemplateDecodingException(renderedTemplate, e), _.asInstanceOf[T])
    }

  }

  // There are a few situations when user may use spring expression:
  // Use case 1: Inside double quotes e.g. {"message": "Hello: #{ #name }"} - here, probably the return type of an expression will be String.
  //             We should skip surrounding quotes and escape quotes in string returned by an expression // TODO: we skip surrounding quotes but escaping is not available yet
  // Use case 2: In some unquoted context of json when a user expects proper json
  //             e.g. { "field": #{ #fieldValue } } or [ #{ #listElement } ]
  //             In this cases we have:
  //             a) implicit logic converting all values to proper json. The only exception is a String which is unquoted (see UC 1)
  //                because we can't recognize which case it is.
  //             b) To avoid rendering invalid jsons, user can explicitly convert value to json by using #CONV.toJsonString()
  // Use case 3. In other places where user want to treat json template the same as string template and use typical templatic logic such as
  //             #{ #condition ? '"field1": 123' : '"field2": 234' } - here implicit logic should work correctly as well
  private def renderExpressionResult(value: AnyRef): String = {
    val encodedJson = ToJsonEncoder.default.encodeUnsafe(value)
    encodedJson.asString
      .getOrElse(encodedJson.noSpaces)
  }

  private[json] class JsonTemplateDecodingException(
      renderedTemplate: String,
      cause: Throwable,
  ) extends NonTransientException(
        input = renderedTemplate,
        message = s"Rendered template [$renderedTemplate] cannot be decoded as json, message: ${cause.getMessage}",
        cause = cause
      )

}
