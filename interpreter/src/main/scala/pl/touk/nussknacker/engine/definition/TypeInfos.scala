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

    protected def isValidMethodInfo(arguments: List[TypingResult], methodTypeInfo: MethodTypeInfo): Boolean = {
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
  }

  case class StaticMethodInfo(signature: MethodTypeInfo,
                              name: String,
                              description: Option[String]) extends MethodInfo {
    override def signatures: NonEmptyList[MethodTypeInfo] = NonEmptyList.one(signature)

    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = {
      if (isValidMethodInfo(arguments, signature)) signature.result.validNel
      else convertError(ArgumentTypeError, arguments).invalidNel
    }
  }

  object FunctionalMethodInfo {
    def apply(typeFunction: List[TypingResult] => ValidatedNel[GenericFunctionTypingError, TypingResult],
              signature: MethodTypeInfo,
              name: String,
              description: Option[String]): FunctionalMethodInfo =
      FunctionalMethodInfo(typeFunction, NonEmptyList.one(signature), name, description)
  }

  case class FunctionalMethodInfo(typeFunction: List[TypingResult] => ValidatedNel[GenericFunctionTypingError, TypingResult],
                                  signatures: NonEmptyList[MethodTypeInfo],
                                  name: String,
                                  description: Option[String]) extends MethodInfo {
    // We use staticInfo.computeResultType to validate against static
    // parameters, so that there is no need to perform basic checks in
    // typeFunction.
    // This is also used to prevents errors in runtime, when typeFunction
    // returns illegal results.
    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = {
      val errorConverter = SpelExpressionParseErrorConverter(this, arguments)
      val typesFromStaticMethodInfo = signatures.filter(isValidMethodInfo(arguments, _)).map(_.result)
      if (typesFromStaticMethodInfo.isEmpty) return convertError(ArgumentTypeError, arguments).invalidNel

      val typeCalculated = typeFunction(arguments).leftMap(_.map(errorConverter.convert))
      typeCalculated.map { calculated =>
        if (!typesFromStaticMethodInfo.exists(calculated.canBeSubclassOf)) {
          val expectedTypesString = typesFromStaticMethodInfo.map(_.display).mkString("(", ", ", ")")
          val argumentsString = arguments.map(_.display).mkString("(", ", ", ")")
          throw new AssertionError(s"Generic function $name returned type ${calculated.display} that does not match any of declared types $expectedTypesString when called with arguments $argumentsString")
        }
      }
      typeCalculated
    }
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
