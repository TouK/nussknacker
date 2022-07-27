package pl.touk.nussknacker.engine.definition

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.generics.{ArgumentTypeError, NoVarArgSignature, ExpressionParseError, VarArgSignature}
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

  object MethodInfo {
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
        NoVarArgsMethodInfo(parameters, refClazz, name, description)
    }
  }

  sealed trait MethodInfo {
    def apply(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult]

    def name: String

    def staticParameters: List[Parameter]

    def staticResult: TypingResult

    def description: Option[String]

    def varArgs: Boolean

    def serializable: SerializableMethodInfo =
      SerializableMethodInfo(staticParameters, staticResult, description, varArgs)
  }

  sealed trait StaticMethodInfo extends MethodInfo {
    protected def checkNoVarArguments(arguments: List[TypingResult], parameters: List[Parameter]): Boolean =
      arguments.length == parameters.length &&
        arguments.zip(parameters).forall{ case(arg, param) => arg.canBeSubclassOf(param.refClazz)}
  }

  case class NoVarArgsMethodInfo(staticParameters: List[Parameter],
                                 staticResult: TypingResult,
                                 name: String,
                                 description: Option[String])
    extends StaticMethodInfo {
    override def apply(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = {
      if (checkNoVarArguments(arguments, staticParameters))
        staticResult.validNel
      else
        new ArgumentTypeError(
          new NoVarArgSignature(name, arguments),
          List(new NoVarArgSignature(name, staticParameters.map(_.refClazz)))
        ).invalidNel
    }

    override def varArgs: Boolean = false
  }

  case class VarArgsMethodInfo(noVarParameters: List[Parameter],
                               varParameter: Parameter,
                               staticResult: TypingResult,
                               name: String,
                               description: Option[String])
    extends StaticMethodInfo {
    private def checkArgumentsLength(arguments: List[TypingResult]): Boolean =
      arguments.length >= noVarParameters.length

    private def checkVarArguments(varArguments: List[TypingResult]): Boolean =
      varArguments.forall(_.canBeSubclassOf(varParameter.refClazz))

    private def checkArguments(arguments: List[TypingResult]): Boolean = {
      val (noVarArguments, varArguments) = arguments.splitAt(noVarParameters.length)
      checkNoVarArguments(noVarArguments, noVarParameters) && checkVarArguments(varArguments)
    }

    override def apply(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = {
      if (checkArgumentsLength(arguments) && checkArguments(arguments))
        staticResult.validNel
      else
        new ArgumentTypeError(
          new NoVarArgSignature(name, arguments),
          List(new VarArgSignature(name, noVarParameters.map(_.refClazz), varParameter.refClazz))
        ).invalidNel
    }

    override def staticParameters: List[Parameter] = {
      val Parameter(varArgName, varArg) = varParameter
      noVarParameters :+ Parameter(varArgName, Typed.genericTypeClass(classOf[Array[Object]], List(varArg)))
    }

    override def varArgs: Boolean = true

  }

  case class FunctionalMethodInfo(typeFunction: List[TypingResult] => ValidatedNel[ExpressionParseError, TypingResult],
                                  staticParameters: List[Parameter],
                                  staticResult: TypingResult,
                                  name: String,
                                  description: Option[String]) extends MethodInfo {
    override def apply(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] =
      typeFunction(arguments)

    override def varArgs: Boolean = false
  }

  case class ClazzDefinition(clazzName: TypedClass,
                             methods: Map[String, List[MethodInfo]],
                             staticMethods: Map[String, List[MethodInfo]]) {
    private def asProperty(info: MethodInfo): Option[TypingResult] = info.apply(List()).toOption

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
