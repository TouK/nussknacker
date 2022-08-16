package pl.touk.nussknacker.engine.definition

import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.catsSyntaxValidatedId
import pl.touk.nussknacker.engine.api.generics.GenericFunctionTypingError.ArgumentTypeError
import pl.touk.nussknacker.engine.api.generics.{ExpressionParseError, GenericFunctionTypingError, MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseErrorConverter

object TypeInfos {
  sealed trait MethodInfo {
    def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult]

    def signatures: NonEmptyList[MethodTypeInfo]

    val mainSignature: MethodTypeInfo = signatures.toList.maxBy(_.parametersToList.length)

    def name: String

    def description: Option[String]

    protected def convertError(error: GenericFunctionTypingError, arguments: List[TypingResult]): ExpressionParseError =
      SpelExpressionParseErrorConverter(this, arguments).convert(error)
  }

  object StaticMethodInfo {
    def apply(signature: MethodTypeInfo, name: String, description: Option[String]): StaticMethodInfo =
      StaticMethodInfo(NonEmptyList.one(signature), name, description)
  }

  case class StaticMethodInfo(signatures: NonEmptyList[MethodTypeInfo],
                              name: String,
                              description: Option[String]) extends MethodInfo {
    private def isValidMethodInfo(arguments: List[TypingResult], methodTypeInfo: MethodTypeInfo): Boolean = {
      val checkNoVarArgs = arguments.length >= methodTypeInfo.noVarArgs.length &&
        arguments.zip(methodTypeInfo.noVarArgs).forall{ case (x, Parameter(_, y)) => x.canBeSubclassOf(y)}

      val checkVarArgs = methodTypeInfo.varArg match {
        case Some(Parameter(_, t)) =>
          arguments.drop(methodTypeInfo.noVarArgs.length).forall(_.canBeSubclassOf(t))
        case None =>
          arguments.length == methodTypeInfo.noVarArgs.length
      }

      checkNoVarArgs && checkVarArgs
    }

    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = {
      signatures
        .find(isValidMethodInfo(arguments, _))
        .map(_.result.validNel)
        .getOrElse(convertError(ArgumentTypeError, arguments).invalidNel)
    }
  }

  object FunctionalMethodInfo {
    def apply(typeFunction: List[TypingResult] => ValidatedNel[GenericFunctionTypingError, TypingResult],
              signature: MethodTypeInfo,
              name: String,
              description: Option[String]): FunctionalMethodInfo =
      FunctionalMethodInfo(typeFunction, NonEmptyList.one(signature), name, description)

    def apply(typeFunction: List[TypingResult] => ValidatedNel[GenericFunctionTypingError, TypingResult],
              signatures: NonEmptyList[MethodTypeInfo],
              name: String,
              description: Option[String]): FunctionalMethodInfo =
      FunctionalMethodInfo(typeFunction, StaticMethodInfo(signatures, name, description))
  }

  case class FunctionalMethodInfo(typeFunction: List[TypingResult] => ValidatedNel[GenericFunctionTypingError, TypingResult],
                                  staticInfo: StaticMethodInfo) extends MethodInfo {
    // We use staticInfo.computeResultType to validate against static
    // parameters, so that there is no need to perform basic checks in
    // typeFunction.
    // This is also used to prevents errors in runtime, when typeFunction
    // returns illegal results.
    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = {
      val errorConverter = SpelExpressionParseErrorConverter(this, arguments)
      val typeFromStaticInfo = staticInfo.computeResultType(arguments)
      // We use and then to make sure that arguments given to typeFunction
      // pass basic validation.
      val typeCalculated = typeFromStaticInfo.andThen(_ => typeFunction(arguments).leftMap(_.map(errorConverter.convert)))
      typeFromStaticInfo.toOption.zip(typeCalculated.toOption).foreach{ case (fromStatic, calculated) =>
        if (!calculated.canBeSubclassOf(fromStatic))
          throw new AssertionError(s"Generic function $name returned type ${calculated.display} that does not match declared type ${fromStatic.display} when called with arguments ${arguments.map(_.display).mkString("(", ", ", ")")}")
      }
      typeCalculated
    }

    override def signatures: NonEmptyList[MethodTypeInfo] = staticInfo.signatures

    override def name: String = staticInfo.name

    override def description: Option[String] = staticInfo.description
  }


  case class ClazzDefinition(clazzName: TypedClass,
                             methods: Map[String, List[MethodInfo]],
                             staticMethods: Map[String, List[MethodInfo]]) {
    private def asProperty(info: MethodInfo): Option[TypingResult] = info.computeResultType(List()).toOption

    def getPropertyOrFieldType(methodName: String): Option[TypingResult] = {
      def filterMethods(candidates: Map[String, List[MethodInfo]]): List[TypingResult] =
        candidates.get(methodName).toList.flatMap(_.map(asProperty)).collect{ case Some(x) => x }
      val filteredMethods = filterMethods(methods)
      val filteredStaticMethods = filterMethods(staticMethods)
      val filtered = filteredMethods ++ filteredStaticMethods
      filtered match {
        case Nil => None
        case nonEmpty => Some(Typed(nonEmpty.toSet))
      }
    }
  }
}
