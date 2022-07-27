package pl.touk.nussknacker.engine.spel.typer

import pl.touk.nussknacker.engine.api.generics.{ArgumentTypeError, NoVarArgSignature, SpelParseError}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.IllegalOperationError.IllegalInvocationError
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.MissingObjectError.UnknownMethodError
import pl.touk.nussknacker.engine.types.EspTypeUtils

object TypeMethodReference {
  def apply(methodName: String,
            invocationTarget: TypingResult,
            params: List[TypingResult],
            isStatic: Boolean,
            methodExecutionForUnknownAllowed: Boolean)
           (implicit settings: ClassExtractionSettings): Either[SpelParseError, TypingResult] =
    new TypeMethodReference(methodName, invocationTarget, params, isStatic, methodExecutionForUnknownAllowed).call
}

class TypeMethodReference(methodName: String,
                          invocationTarget: TypingResult,
                          calledParams: List[TypingResult],
                          isStatic: Boolean,
                          methodExecutionForUnknownAllowed: Boolean) {
  def call(implicit settings: ClassExtractionSettings): Either[SpelParseError, TypingResult] =
    invocationTarget match {
      case tc: SingleTypingResult =>
        typeFromClazzDefinitions(extractClazzDefinitions(Set(tc)))
      case TypedUnion(nestedTypes) =>
        typeFromClazzDefinitions(extractClazzDefinitions(nestedTypes))
      case TypedNull =>
        Left(IllegalInvocationError(TypedNull))
      case Unknown =>
        if(methodExecutionForUnknownAllowed) Right(Unknown) else Left(IllegalInvocationError(Unknown))
    }

  private def extractClazzDefinitions(typedClasses: Set[SingleTypingResult])
                                     (implicit settings: ClassExtractionSettings): List[ClazzDefinition] =
    typedClasses.map(typedClass =>
      EspTypeUtils.clazzDefinition(typedClass.objType.klass)
    ).toList

  private def typeFromClazzDefinitions(clazzDefinitions: List[ClazzDefinition]): Either[SpelParseError, TypingResult] = {
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

  private def validateClassDefinitionsNonEmpty(clazzDefinitions: List[ClazzDefinition]): Either[Option[SpelParseError], List[ClazzDefinition]] =
    if (clazzDefinitions.isEmpty) Left(None) else Right(clazzDefinitions)

  private def validateMethodsNonEmpty(clazzDefinitions: List[ClazzDefinition]): Either[Option[SpelParseError], List[MethodInfo]] = {
    def displayableType = clazzDefinitions.map(k => k.clazzName).map(_.display).mkString(", ")
    def isClass = clazzDefinitions.map(k => k.clazzName).exists(_.canBeSubclassOf(Typed[Class[_]]))

    val clazzMethods =
      if(isStatic) clazzDefinitions.flatMap(_.staticMethods.get(methodName).toList.flatten)
      else clazzDefinitions.flatMap(_.methods.get(methodName).toList.flatten)
    clazzMethods match {
      //Static method can be invoked - we cannot find them ATM
      case Nil if isClass => Left(None)
      case Nil => Left(Some(UnknownMethodError(methodName, displayableType)))
      case methodInfos => Right(methodInfos)
    }
  }

  private def validateMethodParameterTypes(methodInfos: List[MethodInfo]): Either[Option[SpelParseError], List[TypingResult]] = {
    val returnTypesForMatchingMethods = methodInfos.flatMap { m =>
      lazy val allMatching = m.staticParameters.map(_.refClazz).zip(calledParams).forall {
        case (declaredType, passedType) => passedType.canBeSubclassOf(declaredType)
      }
      if (m.staticParameters.size == calledParams.size && allMatching) Some(m.staticResult) else checkForVarArgs(m)
    }
    returnTypesForMatchingMethods match {
      case Nil =>
        def toSignature(params: List[TypingResult]) = new NoVarArgSignature(methodName, params)
        val methodVariances = methodInfos.map(info => toSignature(info.staticParameters.map(_.refClazz)))
        Left(Some(new ArgumentTypeError(toSignature(calledParams), methodVariances)))
      case nonEmpty =>
        Right(nonEmpty)
    }
  }

  private def checkForVarArgs(method: MethodInfo): Option[TypingResult] = {
    val nonVarArgSize = method.staticParameters.size - 1
    if (method.varArgs && calledParams.size >= nonVarArgSize) {
      val nonVarArgParams = method.staticParameters.take(nonVarArgSize)
      val nonVarArgCalledParams = calledParams.take(nonVarArgSize)
      val nonVarArgMatching = nonVarArgParams.map(_.refClazz).zip(nonVarArgCalledParams).forall {
        case (declaredType, passedType) => passedType.canBeSubclassOf(declaredType)
      }
      //TODO: we do not check var arg parameter types
      if (nonVarArgMatching) Some(method.staticResult) else None
    } else {
      None
    }
  }

}
