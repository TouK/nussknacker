package pl.touk.nussknacker.engine.api.definition

import argonaut.derive.{JsonSumCodec, JsonSumCodecFor}
import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.EitherSingleClassOrUnknown
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.CirceUtil._
import EitherSingleClassOrUnknown._

sealed trait NodeDependency

case class TypedNodeDependency(clazz: Class[_]) extends NodeDependency

case object OutputVariableNameDependency extends NodeDependency

object Parameter {

  def unknownType(name: String) = Parameter(name, Unknown, Unknown)

  def apply(name: String, typ: ClazzRef): Parameter = Parameter(name, Typed(typ), Typed(typ))
}


@JsonCodec(encodeOnly = true) case class Parameter(
                      name: String,
                      typ: TypingResult,
                      originalType: TypingResult with EitherSingleClassOrUnknown,
                      restriction: Option[ParameterRestriction] = None,
                      additionalVariables: Map[String, TypingResult] = Map.empty,
                      branchParam: Boolean = false) extends NodeDependency {

  def isLazyParameter: Boolean = originalType == Typed[LazyParameter[_]]

}

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
@ConfiguredJsonCodec sealed trait ParameterRestriction

@JsonCodec case class FixedExpressionValues(values: List[FixedExpressionValue]) extends ParameterRestriction

@JsonCodec case class FixedExpressionValue(expression: String, label: String)