package pl.touk.nussknacker.engine.api.generics

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}

@JsonCodec(encodeOnly = true) case class Parameter(name: String, refClazz: TypingResult)


object ParameterList {
  private val arrayClass = classOf[Array[Object]]

  def fromList(lst: List[Parameter], varArgs: Boolean): ParameterList = (varArgs, lst) match {
    case (true, noVarArgParameters :+ Parameter (paramName, TypedClass (`arrayClass`, varArgType :: Nil) ) ) =>
      ParameterList(noVarArgParameters, Some(Parameter(paramName, varArgType)))
    case (true, _:+ Parameter (_, TypedClass (`arrayClass`, _) ) ) =>
      throw new AssertionError ("Array must have one type parameter")
    case (true, _:+ Parameter (_, _) ) =>
      throw new AssertionError ("VarArg must have type of array")
    case (true, Nil) =>
      throw new AssertionError ("Method with varArgs must have at least one parameter")
    case (false, noVarArgParameters) =>
      ParameterList(noVarArgParameters, None)
  }
}

case class ParameterList(noVarArgs: List[Parameter], varArg: Option[Parameter]) {
  // Returns all parameters including varArg presented as Array[VarArgType];
  // this the same as method.getParameters. This method is used in logic
  // that is independent of whether a function has varArgs.
  def toList: List[Parameter] =
    noVarArgs ::: varArg.map{
      case Parameter(name, refClazz) => Parameter(name, Typed.genericTypeClass[Array[Object]](refClazz :: Nil))
    }.toList
}
