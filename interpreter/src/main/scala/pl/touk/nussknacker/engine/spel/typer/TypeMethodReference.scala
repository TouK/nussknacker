package pl.touk.nussknacker.engine.spel.typer

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.{ArgumentTypeError, OverloadedFunctionError}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.IllegalOperationError.IllegalInvocationError
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.MissingObjectError.UnknownMethodError
import pl.touk.nussknacker.engine.types.EspTypeUtils

object TypeMethodReference {
  def apply(methodName: String,
            invocationTarget: TypingResult,
            params: List[TypingResult],
            isStatic: Boolean,
            methodExecutionForUnknownAllowed: Boolean)
           (implicit settings: ClassExtractionSettings): Either[ExpressionParseError, TypingResult] =
    new TypeMethodReference(methodName, invocationTarget, params, isStatic, methodExecutionForUnknownAllowed).call
}

class TypeMethodReference(methodName: String,
                          invocationTarget: TypingResult,
                          calledParams: List[TypingResult],
                          isStatic: Boolean,
                          methodExecutionForUnknownAllowed: Boolean) {
  def call(implicit settings: ClassExtractionSettings): Either[ExpressionParseError, TypingResult] =
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

  private def typeFromClazzDefinitions(clazzDefinitions: List[ClazzDefinition]): Either[ExpressionParseError, TypingResult] = {
    val validatedType = for {
      nonEmptyClassDefinitions <- validateClassDefinitionsNonEmpty(clazzDefinitions)
      nonEmptyMethods <- validateMethodsNonEmpty(nonEmptyClassDefinitions)
      returnTypesForMatchingParams <- validateMethodParameterTypes(nonEmptyMethods)
    } yield Typed(returnTypesForMatchingParams.toSet)

    validatedType match {
      case Left(None) => Right(Unknown) // we use Left(None) to indicate situation when we want to skip further validations because of lack of some knowledge
      case Left(Some(message)) => Left(message)
      case Right(returnType) => Right(returnType)
    }
  }

  private def validateClassDefinitionsNonEmpty(clazzDefinitions: List[ClazzDefinition]): Either[Option[ExpressionParseError], List[ClazzDefinition]] =
    if (clazzDefinitions.isEmpty) Left(None) else Right(clazzDefinitions)

  private def validateMethodsNonEmpty(clazzDefinitions: List[ClazzDefinition]): Either[Option[ExpressionParseError], List[MethodInfo]] = {
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

  private def validateMethodParameterTypes(methodInfos: List[MethodInfo]): Either[Option[ExpressionParseError], List[TypingResult]] = {
    // We combine MethodInfo with errors so we can use it to decide which
    // error to display.
    val infosWithValidationResults = methodInfos.map(x => (x, x.computeResultType(calledParams)))
    val returnTypes = infosWithValidationResults.map{case (info, typ) => typ.leftMap(_.map((info, _)))}
    val combinedReturnTypes = returnTypes.map(x => x.map(List(_))).reduce((x, y) => (x, y) match {
      case (Valid(xs), Valid(ys)) => Valid(xs ::: ys)
      case (Valid(xs), Invalid(_)) => Valid(xs)
      case (Invalid(_), Valid(ys)) => Valid(ys)
      case (Invalid(xs), Invalid(ys)) => Invalid(xs ::: ys)
    })
    combinedReturnTypes match {
      case Valid(Nil) => Left(None)
      case Valid(xs) => Right(xs)
      case Invalid(xs) => Left(Some(combineErrors(xs)))
    }
  }

  private def combineArgumentTypeErrors(left: ArgumentTypeError, right: ArgumentTypeError): ArgumentTypeError = {
    if (left.name != right.name || left.found != right.found)
      throw new IllegalArgumentException("Cannot combine ArgumentTypeErrors where found signatures differ.")
    ArgumentTypeError(left.name, left.found, left.possibleSignatures ::: right.possibleSignatures)
  }

  // We try to combine ArgumentTypeErrors into one error. If we fail
  // then we return GenericFunctionError. All regular functions return
  // only ArgumentTypeError, so we will lose information about errors
  // only when there is more than one generic function.
  private def combineErrors(errors: NonEmptyList[(MethodInfo, ExpressionParseError)]): ExpressionParseError = errors match {
    case xs if xs.forall(_._2.isInstanceOf[ArgumentTypeError]) =>
      xs.map(_._2.asInstanceOf[ArgumentTypeError]).toList.reduce(combineArgumentTypeErrors)
    case NonEmptyList(head, Nil) =>
      head._2
    case _ =>
      OverloadedFunctionError
  }
}
