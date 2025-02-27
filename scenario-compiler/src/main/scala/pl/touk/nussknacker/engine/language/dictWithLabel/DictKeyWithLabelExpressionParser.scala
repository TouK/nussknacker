package pl.touk.nussknacker.engine.language.dictWithLabel

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{invalidNel, Valid}
import io.circe.parser
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariable => _}
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectWithValue, TypingResult}
import pl.touk.nussknacker.engine.expression.NullExpression
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, ExpressionParser, TypedExpression}
import pl.touk.nussknacker.engine.graph.expression.{DictKeyWithLabelExpression, Expression}
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.KeyWithLabelExpressionParsingError
import pl.touk.nussknacker.engine.spel.SpelExpressionParser

import scala.util.Try

case class DictKeyWithLabelExpressionTypingInfo(key: String, label: Option[String], expectedType: TypingResult)
    extends ExpressionTypingInfo {

  // We should support at least types defined in FragmentParameterValidator#permittedTypesForEditors
  override def typingResult: TypingResult = expectedType match {
    case clazz: TypedClass if clazz.canBeConvertedTo(Typed[Long]) && Try(key.toLong).toOption.isDefined =>
      TypedObjectWithValue(clazz.runtimeObjType, key.toLong)
    case clazz: TypedClass if clazz.canBeConvertedTo(Typed[Boolean]) && Try(key.toBoolean).toOption.isDefined =>
      TypedObjectWithValue(clazz.runtimeObjType, key.toBoolean)
    case clazz: TypedClass if clazz.canBeConvertedTo(Typed[String]) =>
      TypedObjectWithValue(clazz.runtimeObjType, key)
    case _ => expectedType
  }

}

object DictKeyWithLabelExpressionParser extends ExpressionParser {

  override def languageId: Language = Expression.Language.DictKeyWithLabel

  override def parse(
      keyWithLabel: String,
      ctx: ValidationContext,
      expectedType: typing.TypingResult
  ): Validated[NonEmptyList[ExpressionParseError], TypedExpression] =
    if (keyWithLabel.isBlank)
      Valid(
        TypedExpression(
          NullExpression(keyWithLabel, SpelExpressionParser.Standard),
          DictKeyWithLabelExpressionTypingInfo("", None, expectedType)
        )
      )
    else
      parseDictKeyWithLabelExpression(keyWithLabel).map(expr =>
        TypedExpression(
          CompiledDictKeyExpression(expr.key, expectedType),
          DictKeyWithLabelExpressionTypingInfo(expr.key, expr.label, expectedType)
        )
      )

  override def parseWithoutContextValidation(
      keyWithLabel: String,
      expectedType: TypingResult
  ): Validated[NonEmptyList[ExpressionParseError], CompiledExpression] = {
    if (keyWithLabel.isBlank)
      Valid(NullExpression(keyWithLabel, SpelExpressionParser.Standard))
    else
      parseDictKeyWithLabelExpression(keyWithLabel).map(expr => CompiledDictKeyExpression(expr.key, expectedType))
  }

  def parseDictKeyWithLabelExpression(
      keyWithLabelJson: String
  ): Validated[NonEmptyList[KeyWithLabelExpressionParsingError], DictKeyWithLabelExpression] =
    parser.parse(keyWithLabelJson) match {
      case Left(e) => invalidNel(KeyWithLabelExpressionParsingError(keyWithLabelJson, e.message))
      case Right(json) =>
        json.as[DictKeyWithLabelExpression] match {
          case Right(expr) => Valid(expr)
          case Left(e)     => invalidNel(KeyWithLabelExpressionParsingError(keyWithLabelJson, e.message))
        }
    }

  case class CompiledDictKeyExpression(key: String, expectedType: TypingResult) extends CompiledExpression {
    override def language: Language = languageId

    override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = {
      if (expectedType.canBeConvertedTo(Typed[Long])) {
        key.toLong.asInstanceOf[T]
      } else if (expectedType.canBeConvertedTo(Typed[Boolean])) {
        key.toBoolean.asInstanceOf[T]
      } else if (expectedType.canBeConvertedTo(Typed[String])) {
        key.asInstanceOf[T]
      } else {
        throw new IllegalStateException(
          s"DictKeyExpression expected type: ${expectedType.display} is unsupported. It must be a subclass of Long, Boolean or String"
        )
      }
    }

    override def original: String = key
  }

}
