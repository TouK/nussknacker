package pl.touk.nussknacker.engine.api.definition

import argonaut.derive.{JsonSumCodec, JsonSumCodecFor}
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}


object Parameter {
  def unknownType(name: String) = Parameter(name, Unknown, Unknown)

  def apply(name: String, typ: ClazzRef): Parameter = Parameter(name, Typed(typ), Typed(typ))
}

case class Parameter(
                      name: String,
                      typ: TypingResult,
                      originalType: TypingResult,
                      restriction: Option[ParameterRestriction] = None,
                      additionalVariables: Map[String, TypingResult] = Map.empty)

object ParameterRestriction {

  import argonaut._
  import argonaut.ArgonautShapeless._
  import argonaut.Argonaut._
  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] = JsonSumCodecFor(JsonSumCodec.typeField)

  //TODO: cannot make it implicit here easily. Derive macro fails, and implicit resolution enters infinite loop :/
  val codec: CodecJson[ParameterRestriction] = CodecJson.derived[ParameterRestriction]

}

//TODO: add validation of restrictions during compilation...
//this can be used for different restrictions than list of values, e.g. encode '> 0' conditions and so on...
sealed trait ParameterRestriction

case class FixedExpressionValues(values: List[FixedExpressionValue]) extends ParameterRestriction

case class FixedExpressionValue(expression: String, label: String)