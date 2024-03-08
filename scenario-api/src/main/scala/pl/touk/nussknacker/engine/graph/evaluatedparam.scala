package pl.touk.nussknacker.engine.graph

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.expression.Expression

object evaluatedparam {

  object Parameter {
    // FIXME: after adding @JsonCodec
    // one can no longer write: Parameter.tupled, Parameter.apply is no longer recognized,
    // so lest we add this method we'd have to write (Parameter.apply _).tupled
    val tupled: ((ParameterName, Expression)) => Parameter = (Parameter.apply _).tupled
  }

  private implicit val parameterNameCodec: Codec[ParameterName] = Codec.from(
    Decoder.decodeString.map(ParameterName.apply),
    Encoder.encodeString.contramap(_.value)
  )

  // TODO: rename to NodeParameter
  @JsonCodec case class Parameter(name: ParameterName, expression: Expression)

  @JsonCodec case class BranchParameters(branchId: String, parameters: List[Parameter])

}
