package pl.touk.nussknacker.engine.api.expression

import org.springframework.expression.spel.SpelNode
import pl.touk.nussknacker.engine.api.typed.typing.{TypedDict, TypedNull, TypingResult, Unknown}

trait ExpressionParseError {
  def message: String
}


trait TypeError extends ExpressionParseError

sealed trait ArgumentTypeError extends TypeError {
  val found: List[TypingResult]
  val functionName: String

  def expectedString: String

  protected def typesToString(types: List[TypingResult]): String =
    types.map(_.display).mkString(", ")

  override def message: String =
    s"Mismatch parameter types. Found: $functionName(${typesToString(found)}). Required: $functionName($expectedString)"
}

case class NoVarArgumentTypeError(expected: List[TypingResult],
                                  found: List[TypingResult],
                                  functionName: String) extends ArgumentTypeError {
  override def expectedString: String = typesToString(expected)
}

case class VarArgumentTypeError(expected: List[TypingResult],
                                expectedVarArgument: TypingResult,
                                found: List[TypingResult],
                                functionName: String) extends ArgumentTypeError {
  override def expectedString: String = typesToString(expected :+ expectedVarArgument) + "..."
}

case class GenericFunctionError(innerMessage: String) extends ExpressionParseError {
    override def message: String = s"Failed to calculate types in generic function: $innerMessage"
  }
