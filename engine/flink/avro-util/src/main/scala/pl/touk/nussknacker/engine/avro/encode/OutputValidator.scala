package pl.touk.nussknacker.engine.avro.encode

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.SinkOutputParamName
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor

object OutputValidator {

  def validateOutput(output: TypingResult, schema: Schema, encoderPolicy: EncoderPolicy)(implicit nodeId: NodeId): Validated[CustomNodeError, Unit] = {
    val possibleTypes = AvroSchemaTypeDefinitionExtractor.ExtendedPossibleTypes
    val returnType = new AvroSchemaTypeDefinitionExtractor(skippNullableFields = encoderPolicy.acceptUnfilledOptional).typeDefinition(schema, possibleTypes)
    if (!output.canBeSubclassOf(returnType)) {
      Invalid(CustomNodeError("Provided output doesn't match to selected avro schema.", Some(SinkOutputParamName)))
    } else {
      Valid(())
    }.andThen(_ => validateMandatory(output, schema, encoderPolicy))
  }

  //TODO implement me :P
  private def validateMandatory(output: TypingResult, schema: Schema, encoderPolicy: EncoderPolicy): Validated[CustomNodeError, Unit] = {
    Valid(())
  }

}
