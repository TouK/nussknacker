package pl.touk.nussknacker.engine.graph.expression

import io.circe.generic.JsonCodec

@JsonCodec
// label is optional as there is no reason to keep in on BE side, it's resolved for FE in ProcessDictSubstitutor
final case class DictKeyWithLabelExpression(key: String, label: Option[String])
