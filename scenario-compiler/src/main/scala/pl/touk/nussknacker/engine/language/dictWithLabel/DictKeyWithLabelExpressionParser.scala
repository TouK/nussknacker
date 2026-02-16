package pl.touk.nussknacker.engine.language.dictWithLabel

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{invalidNel, Valid}
import io.circe.parser
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariable => _}
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectWithValue, TypingResult}
import pl.touk.nussknacker.engine.expression.ExpectedType
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, ExpressionParser, TypedExpression}
import pl.touk.nussknacker.engine.graph.expression.{DictKeyWithLabelExpression, Expression}
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

import scala.util.Try

case class DictKeyWithLabelExpressionTypingInfo(key: String, label: Option[String], expectedType: TypingResult)
    extends ExpressionTypingInfo {

  // We should support at least types defined in FragmentParameterValidator#permittedTypesForEditors
  override def typingResult: TypingResult = expectedType match {
    case clazz: TypedClass if clazz.canBeLooselyAssignedTo(Typed[Long]) && Try(key.toLong).toOption.isDefined =>
      TypedObjectWithValue(clazz.runtimeObjType, key.toLong)
    case clazz: TypedClass if clazz.canBeLooselyAssignedTo(Typed[Boolean]) && Try(key.toBoolean).toOption.isDefined =>
      TypedObjectWithValue(clazz.runtimeObjType, key.toBoolean)
    case clazz: TypedClass if clazz.canBeLooselyAssignedTo(Typed[String]) =>
      TypedObjectWithValue(clazz.runtimeObjType, key)
    case _ => expectedType
  }

}

object DictKeyWithLabelExpressionParser extends ExpressionParser {

  override def languageId: Language = Expression.Language.DictKeyWithLabel

  override def parse(
      keyWithLabel: String,
      ctx: ValidationContext,
      expectedType: ExpectedType
  ): Validated[NonEmptyList[ExpressionParseError], TypedExpression] =
    handleBlankExpressionAsNullExpression(keyWithLabel).getOrElse {
      parseDictKeyWithLabelExpression(keyWithLabel).map(expr =>
        TypedExpression(
          CompiledDictKeyExpression(expr.key, expectedType.typ),
          DictKeyWithLabelExpressionTypingInfo(expr.key, expr.label, expectedType.typ)
        )
      )
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

  private case class CompiledDictKeyExpression(key: String, expectedType: TypingResult) extends CompiledExpression {
    override def language: Language = languageId

    override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = {
      if (expectedType.canBeLooselyAssignedTo(Typed[Long])) {
        key.toLong.asInstanceOf[T]
      } else if (expectedType.canBeLooselyAssignedTo(Typed[Boolean])) {
        key.toBoolean.asInstanceOf[T]
      } else if (expectedType.canBeLooselyAssignedTo(Typed[String])) {
        key.asInstanceOf[T]
      } else {
        throw new IllegalStateException(
          s"DictKeyExpression expected type: ${expectedType.display} is unsupported. It must be a subclass of Long, Boolean or String"
        )
      }
    }

    override def original: String = key
  }

  case class KeyWithLabelExpressionParsingError(keyWithLabel: String, message: String) extends ExpressionParseError {

    def toProcessCompilationError(
        nodeId: NodeId,
        paramName: ParameterName
    ): ProcessCompilationError.KeyWithLabelExpressionParsingError =
      ProcessCompilationError.KeyWithLabelExpressionParsingError(keyWithLabel, message, paramName, nodeId)

  }

}
