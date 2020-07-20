package pl.touk.nussknacker.engine.avro.encode

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{CanBeSubclassDeterminer, typing}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.SinkValueParamName
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor

object OutputValidator {

  def validateOutput(value: TypingResult, schema: Schema, validationMode: ValidationMode)(implicit nodeId: NodeId): Validated[CustomNodeError, Unit] = {
    val possibleTypes = AvroSchemaTypeDefinitionExtractor.ExtendedPossibleTypes
    //TODO: this still does not handle optional fields validation properly for acceptUnfilledOptional == true.
    //The optional fields types will not be validated, meaning that if e.g. String is used instead of Long, the error will not be detected during typing
    val returnType = new AvroSchemaTypeDefinitionExtractor(skipOptionalFields = validationMode.acceptUnfilledOptional).typeDefinition(schema, possibleTypes)
    if (!new ValidationModeAwareSubclassDeterminer(validationMode).canBeSubclassOf(value, returnType)) {
      Invalid(CustomNodeError("Provided value doesn't match to selected avro schema.", Some(SinkValueParamName)))
    } else {
      Valid(())
    }

  }

  private class ValidationModeAwareSubclassDeterminer(validationMode: ValidationMode) extends CanBeSubclassDeterminer {
    override protected def singleCanBeSubclassOf(givenType: typing.SingleTypingResult, superclassCandidate: typing.SingleTypingResult): Boolean = {
      super.singleCanBeSubclassOf(givenType, superclassCandidate) && ((givenType, superclassCandidate) match {
        case (TypedObjectTypingResult(objFields, _), TypedObjectTypingResult(superFields, _)) if !validationMode.acceptRedundant =>
          objFields.keys.forall(superFields.keys.toSet.contains)
        case _ => true
      })
    }
  }


}
