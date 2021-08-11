package pl.touk.nussknacker.engine.spel.typer

import pl.touk.nussknacker.engine.TypeDefinitionSet
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo}
import pl.touk.nussknacker.engine.types.EspTypeUtils

object TypeMethodReference {
  def apply(methodName: String, invocationTarget: TypingResult, params: List[TypingResult], isStatic: Boolean, methodExecutionForUnknownAllowed: Boolean)(implicit settings: ClassExtractionSettings): Either[String, TypingResult] =
    new TypeMethodReference(methodName, invocationTarget, params, isStatic, methodExecutionForUnknownAllowed).call
}

class TypeMethodReference(methodName: String, invocationTarget: TypingResult, calledParams: List[TypingResult], isStatic: Boolean, methodExecutionForUnknownAllowed: Boolean) {
  def call(implicit settings: ClassExtractionSettings): Either[String, TypingResult] =
    invocationTarget match {
      case tc: SingleTypingResult =>
        typeFromClazzDefinitions(extractClazzDefinitions(Set(tc)))
      case TypedUnion(nestedTypes) =>
        typeFromClazzDefinitions(extractClazzDefinitions(nestedTypes))
      case Unknown =>
        if(methodExecutionForUnknownAllowed) Right(Unknown) else Left("Method invocation on Unknown is not allowed")
    }

  private def extractClazzDefinitions(typedClasses: Set[SingleTypingResult])(implicit settings: ClassExtractionSettings): List[ClazzDefinition] =
    typedClasses.map(typedClass =>
      EspTypeUtils.clazzDefinition(typedClass.objType.klass)
    ).toList

  private def typeFromClazzDefinitions(clazzDefinitions: List[ClazzDefinition]): Either[String, TypingResult] = {
    val validatedType = for {
      nonEmptyClassDefinitions <- validateClassDefinitionsNonEmpty(clazzDefinitions).right
      nonEmptyMethods <- validateMethodsNonEmpty(nonEmptyClassDefinitions).right
      returnTypesForMatchingParams <- validateMethodParameterTypes(nonEmptyMethods).right
    } yield Typed(returnTypesForMatchingParams.toSet)

    validatedType match {
      case Left(None) => Right(Unknown) // we use Left(None) to indicate situation when we want to skip further validations because of lack of some knowledge
      case Left(Some(message)) => Left(message)
      case Right(returnType) => Right(returnType)
    }
  }

  private def validateClassDefinitionsNonEmpty(clazzDefinitions: List[ClazzDefinition]): Either[Option[String], List[ClazzDefinition]] =
    if (clazzDefinitions.isEmpty) Left(None) else Right(clazzDefinitions)

  private def validateMethodsNonEmpty(clazzDefinitions: List[ClazzDefinition]): Either[Option[String], List[MethodInfo]] = {
    def displayableType = clazzDefinitions.map(k => k.clazzName).map(_.display).mkString(", ")
    def isClass = clazzDefinitions.map(k => k.clazzName).exists(_.canBeSubclassOf(Typed[Class[_]]))

    val clazzMethods =
      if(isStatic) clazzDefinitions.flatMap(_.staticMethods.get(methodName).toList.flatten)
      else clazzDefinitions.flatMap(_.methods.get(methodName).toList.flatten)
    clazzMethods match {
      //Static method can be invoked - we cannot find them ATM
      case Nil if isClass => Left(None)
      case Nil => Left(Some(s"Unknown method '$methodName' in $displayableType"))
      case methodInfoes => Right(methodInfoes)
    }
  }

  private def validateMethodParameterTypes(methodInfoes: List[MethodInfo]): Either[Option[String], List[TypingResult]] = {
    val returnTypesForMatchingMethods = methodInfoes.flatMap { m =>
      lazy val allMatching = m.parameters.map(_.refClazz).zip(calledParams).forall {
        case (declaredType, passedType) => passedType.canBeSubclassOf(declaredType)
      }
      if (m.parameters.size == calledParams.size && allMatching) Some(m.refClazz) else checkForVarArgs(m)
    }
    returnTypesForMatchingMethods match {
      case Nil =>
        def toSignature(params: List[TypingResult]) = params.map(_.display).mkString(s"$methodName(", ", ", ")")
        val methodVariances = methodInfoes.map(_.parameters.map(_.refClazz))
        Left(Some(s"Mismatch parameter types. Found: ${toSignature(calledParams)}. Required: ${methodVariances.map(toSignature).mkString(" or ")}"))
      case nonEmpty =>
        Right(nonEmpty)
    }
  }

  private def checkForVarArgs(method: MethodInfo): Option[TypingResult] = {
    val nonVarArgSize = method.parameters.size - 1
    if (method.varArgs && calledParams.size >= nonVarArgSize) {
      val nonVarArgParams = method.parameters.take(nonVarArgSize)
      val nonVarArgCalledParams = calledParams.take(nonVarArgSize)
      val nonVarArgMatching = nonVarArgParams.map(_.refClazz).zip(nonVarArgCalledParams).forall {
        case (declaredType, passedType) => passedType.canBeSubclassOf(declaredType)
      }
      //TODO: we do not check var arg parameter types
      if (nonVarArgMatching) Some(method.refClazz) else None
    } else {
      None
    }
  }

}
