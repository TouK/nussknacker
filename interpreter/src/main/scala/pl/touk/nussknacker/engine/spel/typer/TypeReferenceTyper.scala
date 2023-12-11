package pl.touk.nussknacker.engine.spel.typer

import cats.data.Writer
import org.springframework.expression.spel.ExpressionState
import org.springframework.expression.spel.ast.TypeReference
import org.springframework.expression.{EvaluationContext, EvaluationException}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionSet}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.IllegalOperationError.TypeReferenceError
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.MissingObjectError.UnknownClassError

import scala.util.{Failure, Success, Try}

class TypeReferenceTyper(evaluationContext: EvaluationContext, classDefinitionSet: ClassDefinitionSet) {

  def typeTypeReference(typeReference: TypeReference): Writer[List[ExpressionParseError], TypingResult] = {

    /**
      * getValue mutates TypeReference but is still safe
      * it adds values to fields type and exitTypeDescriptor but field type is transient and exitTypeDescriptor is of a primitive type (String)
      */
    val typeReferenceClazz = Try(typeReference.getValue(new ExpressionState(evaluationContext)))

    typeReferenceClazz match {
      case Success(typeReferenceClazz: Class[_]) =>
        classDefinitionSet.get(typeReferenceClazz) match {
          case Some(clazzDefinition: ClassDefinition) => Writer(List.empty, clazzDefinition.clazzName)
          case None => Writer(List(TypeReferenceError(typeReferenceClazz.toString)), Unknown)
        }
      case Success(other) =>
        throw new IllegalStateException(s"Not expected returned type: $other for TypeReference. Should be Class[_]")
      // SpEL's TypeReference handles in a specific way situation when type starts with lower case and has no dot - it looks for
      // things like T(object), T(double) etc. If it can't find it, it throws IllegalArgumentException
      case Failure(_: IllegalArgumentException) =>
        Writer(List(UnknownClassError(extractClassName(typeReference))), Unknown)
      case Failure(_: EvaluationException) => Writer(List(UnknownClassError(extractClassName(typeReference))), Unknown)
      case Failure(exception)              => throw exception
    }

  }

  // extracts xyz from T(xyz)
  private def extractClassName(typeReference: TypeReference) = {
    val withoutPrefix = typeReference.toStringAST.drop(2)
    withoutPrefix.take(withoutPrefix.length - 1)
  }

}
