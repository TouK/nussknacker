package pl.touk.nussknacker.engine.schemedkafka.sink

import cats.data.{NonEmptyList, ValidatedNel}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.schemedkafka.encode.AvroSchemaOutputValidator
import pl.touk.nussknacker.engine.util.output.OutputValidatorErrorsConverter
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.FieldName

class AvroParameterValidator(schema: Schema, mode: ValidationMode) extends SinkValueData.ParameterValidator {

  private val validator = new AvroSchemaOutputValidator(mode)

  override def validate(fieldName: FieldName, resultType: TypingResult)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    val converter = new OutputValidatorErrorsConverter(fieldName)
    validator.validateTypingResultAgainstSchema(resultType, schema)
      .leftMap(converter.convertValidationErrors)
      .leftMap(NonEmptyList.one)
  }
}
