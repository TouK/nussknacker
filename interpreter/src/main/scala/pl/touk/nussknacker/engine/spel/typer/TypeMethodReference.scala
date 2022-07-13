package pl.touk.nussknacker.engine.spel.typer

import cats.data.Validated.Valid
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
      case TypedNull =>
        Left(s"Method invocation on ${TypedNull.display} is not allowed")
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
    val returnTypesForMatchingMethods = methodInfoes.map(_.apply(calledParams)).collect{ case Valid(x) => x }
    returnTypesForMatchingMethods match {
      case Nil =>
        // FIXME: Better error message.
        Left(Some(s"Illegal parameter types"))
      case nonEmpty => Right(nonEmpty)
    }
  }
}
