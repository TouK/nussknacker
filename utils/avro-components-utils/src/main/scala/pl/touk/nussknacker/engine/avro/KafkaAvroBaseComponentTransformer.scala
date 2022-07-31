package pl.touk.nussknacker.engine.avro

import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.avro.encode.ValidationMode

//todo: move to KafkaUniversalComponentTransformer
object KafkaAvroBaseComponentTransformer {
  final val SchemaVersionParamName = "Schema version"
  final val TopicParamName = "Topic"
  final val SinkKeyParamName = "Key"
  final val SinkValueParamName = "Value"
  final val SinkValidationModeParameterName = "Value validation mode"

  def extractValidationMode(value: String): ValidationMode =
    ValidationMode.byName(value).getOrElse(throw CustomNodeValidationException(s"Unknown validation mode: $value", Some(SinkValidationModeParameterName)))
}
