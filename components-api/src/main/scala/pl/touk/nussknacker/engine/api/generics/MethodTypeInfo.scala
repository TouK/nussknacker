package pl.touk.nussknacker.engine.api.generics

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}

object MethodTypeInfo {
  private val arrayClass = classOf[Array[Object]]

  def fromList(lst: List[Parameter], varArgs: Boolean, result: TypingResult): MethodTypeInfo = (varArgs, lst) match {
    case (true, noVarArgParameters :+ Parameter(paramName, TypedClass(`arrayClass`, varArgType :: Nil, _))) =>
      MethodTypeInfo(noVarArgParameters, Some(Parameter(paramName, varArgType)), result)
    case (true, _ :+ Parameter(_, TypedClass(`arrayClass`, _, _))) =>
      throw new AssertionError("Array must have one type parameter")
    case (true, _ :+ Parameter(_, _)) =>
      throw new AssertionError("VarArg must have type of array")
    case (true, Nil) =>
      throw new AssertionError("Method with varArgs must have at least one parameter")
    case (false, noVarArgParameters) =>
      MethodTypeInfo(noVarArgParameters, None, result)
    case (true, _) => throw new IllegalStateException()
  }

  def withoutVarargs(params: List[Parameter], result: TypingResult): MethodTypeInfo =
    MethodTypeInfo(params, None, result)

  def noArgTypeInfo(returnType: TypingResult) = MethodTypeInfo(
    noVarArgs = Nil,
    varArg = None,
    result = returnType
  )

}

case class MethodTypeInfo(noVarArgs: List[Parameter], varArg: Option[Parameter], result: TypingResult) {

  // Returns all parameters including varArg presented as Array[VarArgType];
  // this the same as method.getParameters. This method is used in logic
  // that is independent of whether a function has varArgs.
  def parametersToList: List[Parameter] =
    noVarArgs ::: varArg.map { case Parameter(name, refClazz) =>
      Parameter(name, Typed.genericTypeClass[Array[Object]](refClazz :: Nil))
    }.toList

}
