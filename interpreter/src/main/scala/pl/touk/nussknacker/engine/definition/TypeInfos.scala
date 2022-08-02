package pl.touk.nussknacker.engine.definition

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.generics.{ArgumentTypeError, ExpressionParseError, Signature}
import pl.touk.nussknacker.engine.api.typed.TypeEncoders
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}

object TypeInfos {
  //a bit sad that it isn't derived automatically, but...
  private implicit val tce: Encoder[TypedClass] = TypeEncoders.typingResultEncoder.contramap[TypedClass](identity)

  @JsonCodec(encodeOnly = true) case class Parameter(name: String, refClazz: TypingResult)

  @JsonCodec(encodeOnly = true) case class SerializableMethodInfo(parameters: List[Parameter],
                                                                  refClazz: TypingResult,
                                                                  description: Option[String],
                                                                  varArgs: Boolean)

  implicit val methodInfoEncoder: Encoder[MethodInfo] = Encoder[SerializableMethodInfo].contramap(_.serializable)

  sealed trait MethodInfo {
    def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult]

    def staticParameters: List[Parameter]

    def staticResult: TypingResult

    def description: Option[String]

    def varArgs: Boolean

    def serializable: SerializableMethodInfo
  }

  sealed trait NoVarArgMethodInfo extends MethodInfo {
    override def varArgs: Boolean = false

    override def serializable: SerializableMethodInfo =
      SerializableMethodInfo(staticParameters, staticResult, description, varArgs)
  }

  sealed trait VarArgMethodInfo extends MethodInfo {
    def staticNoVarArgParameters: List[Parameter]

    def staticVarArgParameter: Parameter

    private def toArray(t: TypingResult): TypedClass =
      Typed.genericTypeClass(classOf[Array[Object]], List(t))

    override def staticParameters: List[Parameter] = {
      val Parameter(varArgName, varArg) = staticVarArgParameter
      staticNoVarArgParameters :+ Parameter(varArgName, toArray(varArg))
    }

    override def varArgs: Boolean = true

    override def serializable: SerializableMethodInfo = {
      val Parameter(varArgName, varArg) = staticVarArgParameter
      SerializableMethodInfo(staticNoVarArgParameters :+ Parameter(varArgName, toArray(varArg)), staticResult, description, varArgs)
    }
  }

  object StaticMethodInfo {
    private val arrayClass = classOf[Array[Object]]

    def apply(parameters: List[Parameter],
              refClazz: TypingResult,
              name: String,
              description: Option[String],
              varArgs: Boolean): StaticMethodInfo = (varArgs, parameters) match {
      case (true, noVarArgParameters :+ Parameter(paramName, TypedClass(`arrayClass`, varArgType :: Nil))) =>
        VarArgsMethodInfo(noVarArgParameters, Parameter(paramName, varArgType), refClazz, name, description)
      case (true, _ :+ Parameter(_, TypedClass(`arrayClass`, _))) =>
        throw new AssertionError("Array must have one type parameter")
      case (true, _ :+ Parameter(_, _)) =>
        throw new AssertionError("VarArg must have type of array")
      case (true, Nil) =>
        throw new AssertionError("Method with varArgs must have at least one parameter")
      case (false, _) =>
        StaticNoVarArgMethodInfo(parameters, refClazz, name, description)
    }
  }

  sealed trait StaticMethodInfo extends MethodInfo {
    protected def checkArgumentsHelper(arguments: List[TypingResult], parameters: List[Parameter]): Boolean =
      arguments.length == parameters.length &&
        arguments.zip(parameters).forall{ case(arg, param) => arg.canBeSubclassOf(param.refClazz)}
  }

  case class StaticNoVarArgMethodInfo(staticParameters: List[Parameter],
                                      staticResult: TypingResult,
                                      name: String,
                                      description: Option[String])
    extends StaticMethodInfo with NoVarArgMethodInfo {
    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = {
      if (checkArgumentsHelper(arguments, staticParameters))
        staticResult.validNel
      else
        new ArgumentTypeError(
          new Signature(name, arguments, None),
          List(new Signature(name, staticParameters.map(_.refClazz), None))
        ).invalidNel
    }
  }

  case class StaticVarArgMethodInfo(staticNoVarArgParameters: List[Parameter],
                                     staticVarArgParameter: Parameter,
                                     staticResult: TypingResult,
                                     name: String,
                                     description: Option[String])
    extends StaticMethodInfo with VarArgMethodInfo {
    private def checkArgumentsLength(arguments: List[TypingResult]): Boolean =
      arguments.length >= staticNoVarArgParameters.length

    private def checkVarArguments(varArguments: List[TypingResult]): Boolean =
      varArguments.forall(_.canBeSubclassOf(staticVarArgParameter.refClazz))

    private def checkArguments(arguments: List[TypingResult]): Boolean = {
      val (noVarArguments, varArguments) = arguments.splitAt(staticNoVarArgParameters.length)
      checkArgumentsHelper(noVarArguments, staticNoVarArgParameters) && checkVarArguments(varArguments)
    }

    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = {
      if (checkArgumentsLength(arguments) && checkArguments(arguments))
        staticResult.validNel
      else
        new ArgumentTypeError(
          new Signature(name, arguments, None),
          List(new Signature(name, noVarParameters.map(_.refClazz), Some(varParameter.refClazz)))
        ).invalidNel
    }
  }

  object FunctionalMethodInfo {
    def apply(typeFunction: List[TypingResult] => ValidatedNel[ExpressionParseError, TypingResult],
              parameters: List[Parameter],
              refClazz: TypingResult,
              name: String,
              description: Option[String],
              varArgs: Boolean): FunctionalMethodInfo =
      FunctionalMethodInfo(typeFunction, StaticMethodInfo(parameters, refClazz, name, description, varArgs))
  }

  case class FunctionalMethodInfo(typeFunction: List[TypingResult] => ValidatedNel[ExpressionParseError, TypingResult],
                                  staticInfo: StaticMethodInfo) extends MethodInfo {
    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] =
      typeFunction(arguments)

    override def staticParameters: List[Parameter] = staticInfo.staticParameters

    override def staticResult: TypingResult = staticInfo.staticResult

    override def description: Option[String] = staticInfo.description

    override def varArgs: Boolean = staticInfo.varArgs

    override def serializable: SerializableMethodInfo = staticInfo.serializable
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
