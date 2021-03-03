package pl.touk.nussknacker.engine.complexexpression

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression._
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.definition.ExpressionLazyParameter
import pl.touk.nussknacker.engine.graph

import scala.collection.JavaConverters._


class ParsedComplexExpression(parsedExpressions: List[List[(TypedExpression, Parameter)]]) extends Expression {

  //This should not be invoked... We should get rid of these methods altogether
  override def language: String = ???

  //This should not be invoked... We should get rid of these methods altogether
  override def original: String = ???

  override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = {
    implicit val nodeId: NodeId = NodeId("")

    val evaluated: java.util.List[java.util.Map[String, Any]] = parsedExpressions.map { expMap =>
      expMap.map { case (TypedExpression(exp, returnType, _), definition) =>
        definition.name -> (if (definition.isLazyParameter)
        //TODO: handle nested?? Should it be here?
        ExpressionLazyParameter(nodeId, definition, graph.expression.Expression(exp.language, exp.original), returnType) else exp.evaluate[Any](ctx, globals))
      }.toMap.asJava
    }.asJava
    evaluated.asInstanceOf[T]
  }
}

object ComplexExpressionTypingInfo extends ExpressionTypingInfo {
  override def typingResult: typing.TypingResult = Unknown
}