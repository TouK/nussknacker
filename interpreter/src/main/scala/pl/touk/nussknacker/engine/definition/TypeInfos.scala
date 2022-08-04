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

    // Returns all parameters including varArg presented as Array[VarArgType];
    // this the same as method.getParameters. This method is used in logic
    // that is independent of whether a function has varArgs.
    final def staticParameters: List[Parameter] =
      staticNoVarArgParameters ::: staticVarArgParameter.map{ case Parameter(name, refClazz) =>
        Parameter(name, Typed.genericTypeClass(classOf[Array[Object]], List(refClazz)))
      }.toList

    def staticNoVarArgParameters: List[Parameter]

    def staticVarArgParameter: Option[Parameter]

    def staticResult: TypingResult

    def description: Option[String]

    final def varArgs: Boolean = staticVarArgParameter.isDefined

    final def serializable: SerializableMethodInfo =
      SerializableMethodInfo(staticParameters, staticResult, description, varArgs)
  }

  object StaticMethodInfo {
    private val arrayClass = classOf[Array[Object]]

    def fromParameterList(parameters: List[Parameter],
                          refClazz: TypingResult,
                          name: String,
                          description: Option[String],
                          varArgs: Boolean): StaticMethodInfo = (varArgs, parameters) match {
      case (true, noVarArgParameters :+ Parameter(paramName, TypedClass(`arrayClass`, varArgType :: Nil))) =>
        StaticMethodInfo(noVarArgParameters, Some(Parameter(paramName, varArgType)), refClazz, name, description)
      case (true, _ :+ Parameter(_, TypedClass(`arrayClass`, _))) =>
        throw new AssertionError("Array must have one type parameter")
      case (true, _ :+ Parameter(_, _)) =>
        throw new AssertionError("VarArg must have type of array")
      case (true, Nil) =>
        throw new AssertionError("Method with varArgs must have at least one parameter")
      case (false, _) =>
        StaticMethodInfo(parameters, None, refClazz, name, description)
    }
  }

  case class StaticMethodInfo(staticNoVarArgParameters: List[Parameter],
                              staticVarArgParameter: Option[Parameter],
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
        new ArgumentTypeError(
          Signature(name, arguments, None),
          Signature(name, staticNoVarArgParameters.map(_.refClazz), staticVarArgParameter.map(_.refClazz)) :: Nil
        ).invalidNel
    }
  }

  object FunctionalMethodInfo {
    def fromParameterList(typeFunction: List[TypingResult] => ValidatedNel[ExpressionParseError, TypingResult],
                          parameters: List[Parameter],
                          refClazz: TypingResult,
                          name: String,
                          description: Option[String],
                          varArgs: Boolean): FunctionalMethodInfo =
      FunctionalMethodInfo(typeFunction, StaticMethodInfo.fromParameterList(parameters, refClazz, name, description, varArgs))
  }

  case class FunctionalMethodInfo(typeFunction: List[TypingResult] => ValidatedNel[ExpressionParseError, TypingResult],
                                  staticInfo: StaticMethodInfo) extends MethodInfo {
    override def computeResultType(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] =
      typeFunction(arguments)

    override def staticNoVarArgParameters: List[Parameter] = staticInfo.staticNoVarArgParameters

    override def staticVarArgParameter: Option[Parameter] = staticInfo.staticVarArgParameter

    override def staticResult: TypingResult = staticInfo.staticResult

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
