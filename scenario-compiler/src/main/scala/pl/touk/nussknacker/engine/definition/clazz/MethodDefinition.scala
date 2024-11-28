package pl.touk.nussknacker.engine.definition.clazz

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.GenericFunctionTypingError.ArgumentTypeError
import pl.touk.nussknacker.engine.api.generics.{
  ExpressionParseError,
  GenericFunctionTypingError,
  MethodTypeInfo,
  Parameter
}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseErrorConverter

sealed trait MethodDefinition {

  def computeResultType(
      instanceType: TypingResult,
      arguments: List[TypingResult]
  ): ValidatedNel[ExpressionParseError, TypingResult]

  def signatures: NonEmptyList[MethodTypeInfo]

  def name: String

  def description: Option[String]

  protected def convertError(error: GenericFunctionTypingError, arguments: List[TypingResult]): ExpressionParseError =
    SpelExpressionParseErrorConverter(this, arguments).convert(error)

  protected def isValidMethodInfo(arguments: List[TypingResult], methodTypeInfo: MethodTypeInfo): Boolean = {
    val checkNoVarArgs = arguments.length >= methodTypeInfo.noVarArgs.length &&
      arguments.zip(methodTypeInfo.noVarArgs).forall {
        // Allow pass array as List argument because of array to list auto conversion:
        // pl.touk.nussknacker.engine.spel.internal.ArrayToListConverter
        case (tc @ TypedClass(klass, _), Parameter(_, y)) if klass.isArray =>
          tc.canBeConvertedTo(y) || Typed
            .genericTypeClass[java.util.List[_]](tc.params)
            .canBeConvertedTo(y)
        case (x, Parameter(_, y)) => x.canBeConvertedTo(y)
      }

    val checkVarArgs = methodTypeInfo.varArg match {
      case Some(Parameter(_, t)) =>
        arguments.drop(methodTypeInfo.noVarArgs.length).forall(_.canBeConvertedTo(t))
      case None =>
        arguments.length == methodTypeInfo.noVarArgs.length
    }

    checkNoVarArgs && checkVarArgs
  }

}

case class StaticMethodDefinition(signature: MethodTypeInfo, name: String, description: Option[String])
    extends MethodDefinition {
  override def signatures: NonEmptyList[MethodTypeInfo] = NonEmptyList.one(signature)

  override def computeResultType(
      instanceType: TypingResult,
      arguments: List[TypingResult]
  ): ValidatedNel[ExpressionParseError, TypingResult] = {
    if (isValidMethodInfo(arguments, signature)) signature.result.validNel
    else convertError(ArgumentTypeError, arguments).invalidNel
  }

}

object FunctionalMethodDefinition {

  def apply(
      typeFunction: (TypingResult, List[TypingResult]) => ValidatedNel[GenericFunctionTypingError, TypingResult],
      signature: MethodTypeInfo,
      name: String,
      description: Option[String]
  ): FunctionalMethodDefinition =
    FunctionalMethodDefinition(typeFunction, NonEmptyList.one(signature), name, description)

}

case class FunctionalMethodDefinition(
    typeFunction: (TypingResult, List[TypingResult]) => ValidatedNel[GenericFunctionTypingError, TypingResult],
    signatures: NonEmptyList[MethodTypeInfo],
    name: String,
    description: Option[String]
) extends MethodDefinition {

  override def computeResultType(
      methodInvocationTarget: TypingResult,
      arguments: List[TypingResult]
  ): ValidatedNel[ExpressionParseError, TypingResult] = {
    val errorConverter            = SpelExpressionParseErrorConverter(this, arguments)
    val typesFromStaticMethodInfo = signatures.filter(isValidMethodInfo(arguments, _)).map(_.result)
    if (typesFromStaticMethodInfo.isEmpty) return convertError(ArgumentTypeError, arguments).invalidNel

    val typeCalculated = typeFunction(methodInvocationTarget, arguments).leftMap(_.map(errorConverter.convert))
    typeCalculated.map { calculated =>
      if (!typesFromStaticMethodInfo.exists(calculated.canBeConvertedTo)) {
        val expectedTypesString = typesFromStaticMethodInfo.map(_.display).mkString("(", ", ", ")")
        val argumentsString     = arguments.map(_.display).mkString("(", ", ", ")")
        throw new AssertionError(
          s"Generic function $name returned type ${calculated.display} that does not match any of declared types $expectedTypesString when called with arguments $argumentsString"
        )
      }
    }
    typeCalculated
  }

}
