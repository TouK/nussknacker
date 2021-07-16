package pl.touk.nussknacker.engine

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import org.springframework.expression.EvaluationContext
import org.springframework.expression.spel.ExpressionState
import org.springframework.expression.spel.ast.TypeReference
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.expression.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypingResult}
import pl.touk.nussknacker.engine.definition.{DefinitionExtractor, ProcessDefinitionExtractor, TypeInfos}
import pl.touk.nussknacker.engine.spel.TypedNode
import pl.touk.nussknacker.engine.spel.ast.SpelAst.RichSpelNode


object TypeDefinitionSet {

  def empty: TypeDefinitionSet = TypeDefinitionSet(Set.empty)

}

case class TypeDefinitionSet(typeDefinitions: Set[TypeInfos.ClazzDefinition]) {

  def validateTypeReference(typeReference: TypeReference, evaluationContext: EvaluationContext): Validated[NonEmptyList[ExpressionParseError], TypedClass] = {

    val typeReferenceClazz = typeReference.getValue(new ExpressionState(evaluationContext))

    typeDefinitions.find(typeDefinition => typeDefinition.clazzName.klass.equals(typeReferenceClazz)) match {
      case Some(clazzDefinition : TypeInfos.ClazzDefinition) => Valid(clazzDefinition.clazzName)
      case _ => Invalid(NonEmptyList.of(ExpressionParseError("Class is not allowed to be passed as TypeReference")))
    }

  }

}

