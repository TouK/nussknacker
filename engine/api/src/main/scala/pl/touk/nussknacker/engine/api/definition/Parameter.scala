package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult


object Parameter {
  def unknownType(name: String) = Parameter(name, ClazzRef[Any], ClazzRef[Any])

  def apply(name: String, typ: ClazzRef): Parameter = Parameter(name, typ, typ)
}

case class Parameter(
                      name: String,
                      typ: ClazzRef,
                      originalType: ClazzRef,
                      restriction: Option[ParameterRestriction] = None,
                      additionalVariables: Map[String, TypingResult] = Map.empty)

//TODO: add validation of restrictions during compilation...
//this can be used for different restrictions than list of values, e.g. encode '> 0' conditions and so on...
sealed trait ParameterRestriction

case class FixedExpressionValues(values: List[FixedExpressionValue]) extends ParameterRestriction

case class FixedExpressionValue(expression: String, label: String)