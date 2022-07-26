package pl.touk.nussknacker.engine

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import org.springframework.expression.{EvaluationContext, EvaluationException}
import org.springframework.expression.spel.ExpressionState
import org.springframework.expression.spel.ast.TypeReference
import pl.touk.nussknacker.engine.api.expression.{ExpressionParseError, TypeReferenceError, UnknownClassError}
import pl.touk.nussknacker.engine.api.typed.typing.TypedClass
import pl.touk.nussknacker.engine.definition.TypeInfo

import scala.util.{Failure, Success, Try}



object TypeDefinitionSet {

  def empty: TypeDefinitionSet = TypeDefinitionSet(Set.empty)

}

case class TypeDefinitionSet(typeDefinitions: Set[TypeInfo.ClazzDefinition]) {

  def validateTypeReference(typeReference: TypeReference, evaluationContext: EvaluationContext): Validated[NonEmptyList[ExpressionParseError], TypedClass] = {

    /**
      * getValue mutates TypeReference but is still safe
      * it adds values to fields type and exitTypeDescriptor but field type is transient and exitTypeDescriptor is of a primitive type (String)
      */
    val typeReferenceClazz = Try(typeReference.getValue(new ExpressionState(evaluationContext)))

    typeReferenceClazz match {
      case Success(typeReferenceClazz) =>
        typeDefinitions.find(typeDefinition => typeDefinition.clazzName.klass.equals(typeReferenceClazz)) match {
          case Some(clazzDefinition: TypeInfo.ClazzDefinition) => Valid(clazzDefinition.clazzName)
          case None => Invalid(NonEmptyList.of(TypeReferenceError(typeReferenceClazz.toString)))
        }
      case Failure(_: EvaluationException) => Invalid(NonEmptyList.of(UnknownClassError(typeReference.toStringAST)))
      case Failure(exception) => throw exception
    }

  }

}

