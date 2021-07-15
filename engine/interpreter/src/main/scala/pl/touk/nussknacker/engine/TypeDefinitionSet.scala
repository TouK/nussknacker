package pl.touk.nussknacker.engine

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import org.springframework.expression.spel.{ExpressionState}
import org.springframework.expression.spel.ast.TypeReference
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.expression.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypingResult}
import pl.touk.nussknacker.engine.definition.{DefinitionExtractor, ProcessDefinitionExtractor, TypeInfos}
import pl.touk.nussknacker.engine.spel.TypedNode
import pl.touk.nussknacker.engine.spel.ast.SpelAst.RichSpelNode
import pl.touk.nussknacker.engine.spel.internal.EvaluationContextPreparer


object TypeDefinitionSet {

  def empty: TypeDefinitionSet = apply(Set.empty)

  def apply(typeDefinitionSet: Set[TypeInfos.ClazzDefinition]): TypeDefinitionSet = {

//    val clazzDefinitionSet = typeDefinitionSet.map(
//      clazzDefinition => clazzDefinition.clazzName)

    new TypeDefinitionSet(typeDefinitionSet)
  }
}

class TypeDefinitionSet(typeDefinitions: Set[TypeInfos.ClazzDefinition]) {

//  def typeDefinitionMap = typeDefinitions

  def validateTypeReference(typeReference: TypeReference, evaluationContextPreparer: EvaluationContextPreparer): Validated[NonEmptyList[ExpressionParseError], TypedClass] = {

    val evaluationContext = evaluationContextPreparer.prepareEvaluationContext(Context(""), Map.empty)

    val typeReferenceClazz = typeReference.getValue(new ExpressionState(evaluationContext))

    typeDefinitions.find(typeDefinition => typeDefinition.clazzName.klass.equals(typeReferenceClazz)) match {
      case Some(clazzDefinition : TypeInfos.ClazzDefinition) => Valid(clazzDefinition.clazzName)
      case _ => Invalid(NonEmptyList.of(ExpressionParseError("Class is not allowed to be passed as TypeReference")))
    }

  }

}

