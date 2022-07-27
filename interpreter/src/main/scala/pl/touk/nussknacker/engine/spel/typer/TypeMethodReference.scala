package pl.touk.nussknacker.engine.spel.typer

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.SpelParseError
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.definition.TypeInfo.{ClazzDefinition, MethodInfo}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.IllegalOperationError.IllegalInvocationError
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.MissingObjectError.UnknownMethodError
import pl.touk.nussknacker.engine.spel.typer.TypeMethodReference.NoDataForEvaluation
import pl.touk.nussknacker.engine.types.EspTypeUtils

object TypeMethodReference {
  def apply(methodName: String,
            invocationTarget: TypingResult,
            params: List[TypingResult],
            isStatic: Boolean,
            methodExecutionForUnknownAllowed: Boolean)
           (implicit settings: ClassExtractionSettings): ValidatedNel[SpelParseError, TypingResult] =
    new TypeMethodReference(methodName, invocationTarget, params, isStatic, methodExecutionForUnknownAllowed).call

  private case class NoDataForEvaluation()
}

class TypeMethodReference(methodName: String,
                          invocationTarget: TypingResult,
                          calledParams: List[TypingResult],
                          isStatic: Boolean,
                          methodExecutionForUnknownAllowed: Boolean) {
  def call(implicit settings: ClassExtractionSettings): ValidatedNel[SpelParseError, TypingResult] =
    invocationTarget match {
      case tc: SingleTypingResult =>
        typeFromClazzDefinitions(extractClazzDefinitions(Set(tc)))
      case TypedUnion(nestedTypes) =>
        typeFromClazzDefinitions(extractClazzDefinitions(nestedTypes))
      case TypedNull =>
        IllegalInvocationError(TypedNull).invalidNel
      case Unknown =>
        if(methodExecutionForUnknownAllowed) Unknown.validNel else IllegalInvocationError(Unknown).invalidNel
    }

  private def extractClazzDefinitions(typedClasses: Set[SingleTypingResult])(implicit settings: ClassExtractionSettings): List[ClazzDefinition] =
    typedClasses.map(typedClass =>
      EspTypeUtils.clazzDefinition(typedClass.objType.klass)
    ).toList

  private def typeFromClazzDefinitions(clazzDefinitions: List[ClazzDefinition]): ValidatedNel[SpelParseError, TypingResult] =
    validateClassDefinitionsNonEmpty(clazzDefinitions)
      .andThen(validateMethodsNonEmpty)
      .andThen(validateMethodParameterTypes)
      .map(types => Typed(types.toList.toSet)) match {
      case valid@Valid(_) => valid
      case Invalid(Right(NoDataForEvaluation())) => Unknown.validNel
      case Invalid(Left(errors)) => Invalid(errors)
    }

  private def validateClassDefinitionsNonEmpty(clazzDefinitions: List[ClazzDefinition]):
    Validated[Either[NonEmptyList[SpelParseError], NoDataForEvaluation], NonEmptyList[ClazzDefinition]] = {
    NonEmptyList.fromList(clazzDefinitions).map(_.valid).getOrElse(Right(NoDataForEvaluation()).invalid)
  }

  private def validateMethodsNonEmpty(clazzDefinitions: NonEmptyList[ClazzDefinition]):
    Validated[Either[NonEmptyList[SpelParseError], NoDataForEvaluation], NonEmptyList[MethodInfo]] = {
    def displayableType = clazzDefinitions.map(k => k.clazzName).map(_.display).toList.mkString(", ")
    def isClass = clazzDefinitions.map(k => k.clazzName).exists(_.canBeSubclassOf(Typed[Class[_]]))
    def filterMethods(methods: Map[String, List[MethodInfo]], name: String): List[MethodInfo] =
      methods.get(name).toList.flatten

    val clazzMethods =
      if(isStatic) clazzDefinitions.toList.flatMap(x => filterMethods(x.staticMethods, methodName))
      else clazzDefinitions.toList.flatMap(x => filterMethods(x.methods, methodName))

    NonEmptyList.fromList(clazzMethods).map(_.valid).getOrElse(
      if (isClass) Right(NoDataForEvaluation()).invalid
      else Left(NonEmptyList.one(UnknownMethodError(methodName, displayableType))).invalid
    )
  }

  private def validateMethodParameterTypes(methodInfoes: NonEmptyList[MethodInfo]):
    Validated[Either[NonEmptyList[SpelParseError], NoDataForEvaluation], NonEmptyList[TypingResult]] = {
    val returnTypesForMatchingMethods = methodInfoes.map(_.apply(calledParams))
    val validReturnTypesForMatchingMethods = returnTypesForMatchingMethods.collect{ case Valid(x) => x }
    validReturnTypesForMatchingMethods match {
      case Nil =>
        val collectedErrors = returnTypesForMatchingMethods
          .collect{ case Invalid(lst) => lst }
          .reduce((x, y) => x ::: y)
        Left(collectedErrors).invalid
      case x :: xs => NonEmptyList(x, xs).valid
    }
  }
}
