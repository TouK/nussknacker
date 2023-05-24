package pl.touk.nussknacker.engine.spel.typer

import cats.data.Writer
import org.springframework.expression.spel.ExpressionState
import org.springframework.expression.spel.ast.TypeReference
import org.springframework.expression.{EvaluationContext, EvaluationException}
import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.TypeInfos
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.IllegalOperationError.TypeReferenceError
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.MissingObjectError.UnknownClassError

import scala.util.{Failure, Success, Try}

class TypeReferenceTyper(evaluationContext: EvaluationContext,
                         typeDefinitionSet: TypeDefinitionSet) {

  def typeTypeReference(typeReference: TypeReference): Writer[List[ExpressionParseError], TypingResult] = {

    /**
      * getValue mutates TypeReference but is still safe
      * it adds values to fields type and exitTypeDescriptor but field type is transient and exitTypeDescriptor is of a primitive type (String)
      */
    val typeReferenceClazz = Try(typeReference.getValue(new ExpressionState(evaluationContext)))

    typeReferenceClazz match {
      case Success(typeReferenceClazz: Class[_]) =>
        typeDefinitionSet.get(typeReferenceClazz) match {
          case Some(clazzDefinition: TypeInfos.ClazzDefinition) => Writer(List.empty, clazzDefinition.clazzName)
          case None => Writer(List(TypeReferenceError(typeReferenceClazz.toString)), Unknown)
        }
      case Success(other) => throw new IllegalStateException(s"Not expected returned type: $other for TypeReference. Should be Class[_]")
      case Failure(_: EvaluationException) => Writer(List(UnknownClassError(typeReference.toStringAST)), Unknown)
      case Failure(exception) => throw exception
    }

  }

}
