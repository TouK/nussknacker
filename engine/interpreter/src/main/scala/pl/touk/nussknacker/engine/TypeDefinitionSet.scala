package pl.touk.nussknacker.engine

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import org.apache.commons.lang3.ClassUtils
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

  def withDefaultClasses: TypeDefinitionSet = {

    def createTestClazzDefinitionFromClassNames(className: String) =
      TypeInfos.ClazzDefinition(TypedClass(ClassUtils.primitiveToWrapper(ClassUtils.getClass(className)), Nil), Map.empty)

    val stringClazzDefinition = createTestClazzDefinitionFromClassNames("java.lang.String")
    val longClazzDefinition = createTestClazzDefinitionFromClassNames("java.lang.Long")

    TypeDefinitionSet(Set(stringClazzDefinition, longClazzDefinition))
  }

}

case class TypeDefinitionSet(typeDefinitions: Set[TypeInfos.ClazzDefinition]) {

  def validateTypeReference(typeReference: TypeReference, evaluationContext: EvaluationContext): Validated[NonEmptyList[ExpressionParseError], TypedClass] = {

    val typeReferenceClazz = typeReference.getValue(new ExpressionState(evaluationContext))

    typeDefinitions.find(typeDefinition => typeDefinition.clazzName.klass.equals(typeReferenceClazz)) match {
      case Some(clazzDefinition : TypeInfos.ClazzDefinition) => Valid(clazzDefinition.clazzName)
      case _ => Invalid(NonEmptyList.of(ExpressionParseError(s"Class ${typeReferenceClazz} is not allowed to be passed as TypeReference")))
    }

  }

}

//ClazzDefinition(TypedClass(class scala.Option,List()),Map(empty -> List(MethodInfo(List(),TypedClass(boolean,List()),None,false)), isDefined -> List(MethodInfo(List(),TypedClass(boolean,List()),None,false)), defined -> List(MethodInfo(List(),TypedClass(boolean,List()),None,false)), toString -> List(MethodInfo(List(),TypedClass(class java.lang.String,List()),None,false)), get -> List(MethodInfo(List(),Unknown,None,false)), contains -> List(MethodInfo(List(Parameter(elem,Unknown)),TypedClass(boolean,List()),None,false)), isEmpty -> List(MethodInfo(List(),TypedClass(boolean,List()),None,false))))