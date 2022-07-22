package pl.touk.nussknacker.engine.definition

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.expression.{ExpressionParseError, NoVarArgumentTypeError, VarArgumentTypeError}
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
              varArgs: Boolean): StaticMethodInfo = parameters match {
      case noVarArgParameters :+ Parameter(name, TypedClass(`arrayClass`, varArgTypes)) if varArgs =>
        VarArgsMethodInfo(noVarArgParameters, Parameter(name, Typed(varArgTypes.toSet)), refClazz, name, description)
      case _ =>
        SimpleMethodInfo(parameters, refClazz, name, description)
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

    def asProperty: Option[TypingResult] = apply(List()).toOption
  }

  sealed trait StaticMethodInfo extends MethodInfo {
    protected def checkNoVarArguments(arguments: List[TypingResult], parameters: List[Parameter]): Boolean =
      arguments.length == parameters.length &&
        arguments.zip(parameters).forall{ case(arg, param) => arg.canBeSubclassOf(param.refClazz)}
  }

  case class SimpleMethodInfo(staticParameters: List[Parameter],
                              staticResult: TypingResult,
                              name: String,
                              description: Option[String])
    extends StaticMethodInfo {
    override def apply(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = {
      if (checkNoVarArguments(arguments, staticParameters)) staticResult.validNel
      else NoVarArgumentTypeError(staticParameters.map(_.refClazz), arguments, name).invalidNel
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

    private def checkVarArguments(varArguments: List[TypingResult]): Boolean = {
      println(varParameter.refClazz.display)
      varArguments.foreach(x => println(s"$x.display ${x.canBeSubclassOf(varParameter.refClazz)}"))
      varArguments.forall(_.canBeSubclassOf(varParameter.refClazz))
    }

    private def checkArguments(arguments: List[TypingResult]): Boolean = {
      val (noVarArguments, varArguments) = arguments.splitAt(noVarParameters.length)
      checkNoVarArguments(noVarArguments, noVarParameters) && checkVarArguments(varArguments)
    }

    override def apply(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = {
      if (checkArgumentsLength(arguments) && checkArguments(arguments)) staticResult.validNel
      else VarArgumentTypeError(noVarParameters.map(_.refClazz), varParameter.refClazz, arguments, name).invalidNel
    }

    override def staticParameters: List[Parameter] = {
      val Parameter(varArgName, varArg) = varParameter
      noVarParameters :+ Parameter(varArgName, TypedClass.applyForArray(List(varArg)))
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
    def getPropertyOrFieldType(methodName: String): Option[TypingResult] = {
      def filterMethods(candidates: Map[String, List[MethodInfo]]): List[TypingResult] =
        candidates.get(methodName).toList.flatMap(_.map(_.asProperty)).collect{ case Some(x) => x }
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
