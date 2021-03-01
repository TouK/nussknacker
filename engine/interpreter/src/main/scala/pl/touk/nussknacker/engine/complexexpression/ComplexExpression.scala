package pl.touk.nussknacker.engine.complexexpression

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import io.circe.Decoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression._
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypedUnion, Unknown}
import pl.touk.nussknacker.engine.definition.ExpressionLazyParameter
import pl.touk.nussknacker.engine.graph
import pl.touk.nussknacker.engine.graph.evaluatedparam

import scala.collection.JavaConverters._

object ComplexExpressionParser {

  val languageId: String = "complex"

}

class ComplexExpressionParser(otherParsers: Seq[ExpressionParser]) extends ExpressionParser {

  override def parse(original: String, ctx: ValidationContext, parameter: Parameter): ValidatedNel[ExpressionParseError, TypedExpression] = {
    val expression = io.circe.parser.parse(original).flatMap(Decoder[ComplexExpression].decodeJson)
    val children = parameter.childArrayParameters.getOrElse(Nil)
    implicit val nodeId: NodeId = NodeId("")
    expression match {
      case Left(error) => Validated.invalidNel(ExpressionParseError(error.toString))
      case Right(value) =>
        val parts = value.parts.map { part =>
          part.map { case evaluatedparam.Parameter(name, graph.expression.Expression(language, expression)) =>
            val param = children.find(_.name == name).get
            parsers(language).parse(expression, ctx, param).map { parsed => (parsed, param) }
          }.sequence
        }.sequence
        parts.map { parts =>
          val partsType = parts.map(k => TypedObjectTypingResult(k.map(el => el._2.name -> el._1.returnType).toMap))
          val returnType = TypedClass(classOf[java.util.List[_]], List(Typed(partsType.toArray:_*)))
          TypedExpression(new ParsedComplexExpression(original, parts), returnType, ComplexExpressionTypingInfo)
        }
    }
  }

  private val parsers: Map[String, ExpressionParser] = otherParsers.map(p => p.languageId -> p).toMap + (languageId -> this)

  override def languageId: String = ComplexExpressionParser.languageId

  override def parseWithoutContextValidation(original: String, parameter: Parameter): ValidatedNel[ExpressionParseError, Expression] = {
    throw new IllegalArgumentException("Should not be invoked in lazy context")
  }

}

class ParsedComplexExpression(val original: String, parsedExpressions: List[List[(TypedExpression, Parameter)]]) extends Expression {

  override def language: String = ComplexExpressionParser.languageId

  override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = {
    implicit val nodeId: NodeId = NodeId("")

    val evaluated: java.util.List[java.util.Map[String, Any]] = parsedExpressions.map { expMap =>
      expMap.map { case (TypedExpression(exp, returnType, _), p) =>
        p.name -> (if (p.isLazyParameter)
        ExpressionLazyParameter(nodeId, p, graph.expression.Expression(exp.language, exp.original), returnType) else exp.evaluate[Any](ctx, globals))
      }.toMap.asJava
    }.asJava
    evaluated.asInstanceOf[T]
  }
}

@JsonCodec case class ComplexExpression(parts: List[List[evaluatedparam.Parameter]])

object ComplexExpressionTypingInfo extends ExpressionTypingInfo {
  override def typingResult: typing.TypingResult = Unknown
}