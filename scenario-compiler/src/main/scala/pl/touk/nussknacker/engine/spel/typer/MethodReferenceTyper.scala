package pl.touk.nussknacker.engine.spel.typer

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionSet, MethodDefinition}
import pl.touk.nussknacker.engine.extension.CastTyping
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.IllegalOperationError.IllegalInvocationError
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.MissingObjectError.UnknownMethodError
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.{ArgumentTypeError, OverloadedFunctionError}

class MethodReferenceTyper(classDefinitionSet: ClassDefinitionSet, methodExecutionForUnknownAllowed: Boolean) {

  def typeMethodReference(reference: MethodReference): Either[ExpressionParseError, TypingResult] = {
    implicit val implicitReference: MethodReference = reference
    reference.invocationTarget match {
      case tc: SingleTypingResult =>
        typeFromClazzDefinitions(extractClazzDefinitions(NonEmptyList.one(tc)))
      case union: TypedUnion =>
        typeFromClazzDefinitions(extractClazzDefinitions(union.possibleTypes))
      case TypedNull =>
        Left(IllegalInvocationError(TypedNull))
      case Unknown =>
        if (CastTyping.isCastMethod(reference)) CastTyping.Instance.computeResultType(reference)
        else if (methodExecutionForUnknownAllowed) Right(Unknown)
        // todo: lbg suggestions?
//        if (methodExecutionForUnknownAllowed) Right(Unknown)
        else Left(IllegalInvocationError(Unknown))
    }
  }

  private def extractClazzDefinitions(typedClasses: NonEmptyList[SingleTypingResult]): List[ClassDefinition] = {
    typedClasses.toList.flatMap(tc => classDefinitionSet.get(tc.typeHintsObjType.klass))
  }

  private def typeFromClazzDefinitions(
      clazzDefinitions: List[ClassDefinition]
  )(implicit reference: MethodReference): Either[ExpressionParseError, TypingResult] = {
    val validatedType = for {
      nonEmptyClassDefinitions     <- validateClassDefinitionsNonEmpty(clazzDefinitions)
      nonEmptyMethods              <- validateMethodsNonEmpty(nonEmptyClassDefinitions)
      returnTypesForMatchingParams <- validateMethodParameterTypes(nonEmptyMethods)
    } yield Typed(returnTypesForMatchingParams)

    validatedType match {
      case Left(None) =>
        Right(
          Unknown
        ) // we use Left(None) to indicate situation when we want to skip further validations because of lack of some knowledge
      case Left(Some(message)) => Left(message)
      case Right(returnType)   => Right(returnType)
    }
  }

  private def validateClassDefinitionsNonEmpty(
      clazzDefinitions: List[ClassDefinition]
  ): Either[Option[ExpressionParseError], NonEmptyList[ClassDefinition]] = {
    NonEmptyList.fromList(clazzDefinitions).map(nel => Right(nel)).getOrElse(Left(None))
  }

  private def validateMethodsNonEmpty(
      clazzDefinitions: NonEmptyList[ClassDefinition]
  )(implicit reference: MethodReference): Either[Option[ExpressionParseError], NonEmptyList[MethodDefinition]] = {
    def displayableType = clazzDefinitions.map(k => k.clazzName).map(_.display).toList.mkString(", ")

    def isClass = clazzDefinitions.map(k => k.clazzName).exists(_.canBeSubclassOf(Typed[Class[_]]))

    val clazzMethods =
      if (reference.isStatic) clazzDefinitions.toList.flatMap(_.staticMethods.get(reference.methodName).toList.flatten)
      else clazzDefinitions.toList.flatMap(_.methods.get(reference.methodName).toList.flatten)
    clazzMethods match {
      // Static method can be invoked - we cannot find them ATM
      case Nil if isClass => Left(None)
      case Nil            => Left(Some(UnknownMethodError(reference.methodName, displayableType)))
      case first :: rest  => Right(NonEmptyList(first, rest))
    }
  }

  private def validateMethodParameterTypes(
      methods: NonEmptyList[MethodDefinition]
  )(implicit reference: MethodReference): Either[Option[ExpressionParseError], NonEmptyList[TypingResult]] = {
    // We combine MethodDefinition with errors so we can use it to decide which
    // error to display.
    val methodDefsWithValidationResults =
      methods.map(x => (x, x.computeResultType(reference.invocationTarget, reference.params)))
    val returnTypes = methodDefsWithValidationResults.map { case (methodDef, validType) =>
      validType.leftMap(_.map((methodDef, _)))
    }
    val combinedReturnTypes = returnTypes
      .map(x => x.map(NonEmptyList.one))
      .reduce(
        (
            x: ValidatedNel[(MethodDefinition, ExpressionParseError), NonEmptyList[TypingResult]],
            y: ValidatedNel[(MethodDefinition, ExpressionParseError), NonEmptyList[TypingResult]]
        ) =>
          (x, y) match {
            case (Valid(xs), Valid(ys))     => Valid(xs ::: ys)
            case (Valid(xs), Invalid(_))    => Valid(xs)
            case (Invalid(_), Valid(ys))    => Valid(ys)
            case (Invalid(xs), Invalid(ys)) => Invalid(xs ::: ys)
          }
      )
    combinedReturnTypes match {
      case Valid(xs)   => Right(xs)
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
  private def combineErrors(errors: NonEmptyList[(MethodDefinition, ExpressionParseError)]): ExpressionParseError =
    errors match {
      case xs if xs.forall(_._2.isInstanceOf[ArgumentTypeError]) =>
        xs.map(_._2.asInstanceOf[ArgumentTypeError]).toList.reduce(combineArgumentTypeErrors)
      case NonEmptyList(head, Nil) =>
        head._2
      case _ =>
        OverloadedFunctionError
    }

}

case class MethodReference(
    invocationTarget: TypingResult,
    isStatic: Boolean,
    methodName: String,
    params: List[TypingResult]
)
