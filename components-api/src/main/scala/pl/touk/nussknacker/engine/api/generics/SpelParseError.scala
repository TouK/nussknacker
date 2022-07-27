package pl.touk.nussknacker.engine.api.generics

trait SpelParseError {
  def message: String
}

abstract class ArgumentTypeError extends SpelParseError {
  def found: List[String]

  def functionName: String

  def expectedString: String

  protected def typesToString(types: List[String]): String =
    types.mkString(", ")

  override def message: String =
    s"Mismatch parameter types. Found: $functionName(${typesToString(found)}). Required: $functionName($expectedString)"
}

final class NoVarArgumentTypeError(expectedInner: List[String],
                                   foundInner: List[String],
                                   functionNameInner: String) extends ArgumentTypeError {
  override def expectedString: String = typesToString(expectedInner)

  override def found: List[String] = foundInner

  override def functionName: String = functionNameInner
}

final class VarArgumentTypeError(expectedInner: List[String],
                                 expectedVarArgInner: String,
                                 foundInner: List[String],
                                 functionNameInner: String) extends ArgumentTypeError {
  override def found: List[String] = foundInner

  override def functionName: String = functionNameInner

  override def expectedString: String = typesToString(expectedInner :+ expectedVarArgInner) + "..."
}

class GenericFunctionError(messageInner: String) extends SpelParseError {
  override def message: String = messageInner
}


