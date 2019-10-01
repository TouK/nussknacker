package pl.touk.nussknacker.engine.api.definition

import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.CirceUtil._

sealed trait NodeDependency

case class TypedNodeDependency(clazz: Class[_]) extends NodeDependency

case object OutputVariableNameDependency extends NodeDependency

object Parameter {
  def apply(name: String, typ: ClazzRef): Parameter = Parameter(name, Typed(typ), typ.clazz)
}

case class Parameter(name: String,
                     typ: TypingResult,
                     runtimeClass: Class[_],
                     restriction: Option[ParameterRestriction] = None,
                     additionalVariables: Map[String, TypingResult] = Map.empty,
                     branchParam: Boolean = false) extends NodeDependency {

  def isLazyParameter: Boolean = classOf[LazyParameter[_]].isAssignableFrom(runtimeClass)

}

//TODO: add validation of restrictions during compilation...
//this can be used for different restrictions than list of values, e.g. encode '> 0' conditions and so on...
@ConfiguredJsonCodec sealed trait ParameterRestriction

@JsonCodec case class FixedExpressionValues(values: List[FixedExpressionValue]) extends ParameterRestriction

@JsonCodec case class FixedExpressionValue(expression: String, label: String)