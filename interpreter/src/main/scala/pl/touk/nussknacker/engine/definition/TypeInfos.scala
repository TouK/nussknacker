package pl.touk.nussknacker.engine.definition

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.generics.{ExpressionParseError, GenericFunctionTypingError, Parameter, ParameterList}
import pl.touk.nussknacker.engine.api.typed.TypeEncoders
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseErrorConverter

object TypeInfos {
  //a bit sad that it isn't derived automatically, but...
  private implicit val tce: Encoder[TypedClass] = TypeEncoders.typingResultEncoder.contramap[TypedClass](identity)

  @JsonCodec(encodeOnly = true) case class SerializableMethodInfo(parameters: List[Parameter],
                                                                  refClazz: TypingResult,
                                                                  description: Option[String],
                                                                  varArgs: Boolean)

  implicit val methodInfoEncoder: Encoder[MethodInfo] = Encoder[SerializableMethodInfo].contramap(_.serializable)

  sealed trait MethodInfo {
    def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult]

    def staticParameters: ParameterList

    final def staticNoVarArgParameters: List[Parameter] = staticParameters.noVarArgs

    final def staticVarArgParameter: Option[Parameter] = staticParameters.varArg

    def staticResult: TypingResult

    def name: String

    def description: Option[String]

    final def varArgs: Boolean = staticVarArgParameter.isDefined

    final def serializable: SerializableMethodInfo =
      SerializableMethodInfo(staticParameters.toList, staticResult, description, varArgs)
  }

  case class StaticMethodInfo(staticParameters: ParameterList,
                              staticResult: TypingResult,
                              name: String,
                              description: Option[String]) extends MethodInfo {
    private def checkNoVarArgs(arguments: List[TypingResult]): Boolean =
      arguments.length >= staticNoVarArgParameters.length &&
        arguments.zip(staticNoVarArgParameters).forall{ case (x, Parameter(_, y)) => x.canBeSubclassOf(y)}

    private def checkVarArgs(arguments: List[TypingResult]): Boolean = staticVarArgParameter match {
      case Some(Parameter(_, t)) =>
        arguments.drop(staticNoVarArgParameters.length).forall(_.canBeSubclassOf(t))
      case None =>
        arguments.length == staticNoVarArgParameters.length
    }

    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = {
      if (checkNoVarArgs(arguments) && checkVarArgs(arguments))
        staticResult.validNel
      else
        SpelExpressionParseErrorConverter(this, arguments).convert(GenericFunctionTypingError.ArgumentTypeError).invalidNel
    }
  }

  object FunctionalMethodInfo {
    def apply(typeFunction: List[TypingResult] => ValidatedNel[GenericFunctionTypingError, TypingResult],
              staticParameters: ParameterList,
              staticResult: TypingResult,
              name: String,
              description: Option[String]): FunctionalMethodInfo =
      FunctionalMethodInfo(typeFunction, StaticMethodInfo(staticParameters, staticResult, name, description))
  }

  case class FunctionalMethodInfo(typeFunction: List[TypingResult] => ValidatedNel[GenericFunctionTypingError, TypingResult],
                                  staticInfo: StaticMethodInfo) extends MethodInfo {
    // We use staticInfo.computeResultType to validate against static
    // parameters, so that there is no need to perform basic checks in
    // typeFunction.
    // This is also used to prevents errors in runtime, when typeFunction
    // returns illegal results.
    // TODO: Validate that staticParameters and staticResult match real type of function.
    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] =
      staticInfo.computeResultType(arguments)
        .andThen(_ => {
          typeFunction(arguments).leftMap(_.map(SpelExpressionParseErrorConverter(this, arguments).convert(_)))
        })
        .map(res => {
          if (!res.canBeSubclassOf(staticResult)) throw new AssertionError("Generic function returned type that does not match static parameters.")
          res
        })

    override def staticParameters: ParameterList = staticInfo.staticParameters

    override def staticResult: TypingResult = staticInfo.staticResult

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
