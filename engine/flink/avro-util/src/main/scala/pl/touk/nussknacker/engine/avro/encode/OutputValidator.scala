package pl.touk.nussknacker.engine.avro.encode

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.typed.{CanBeSubclassDeterminer, SingleSubclassRestriction, typing}
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.SinkValueParamName
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor

object OutputValidator {

  def validateOutput(value: TypingResult, schema: Schema, validationMode: ValidationMode)(implicit nodeId: NodeId): Validated[CustomNodeError, Unit] = {
    val possibleTypes = AvroSchemaTypeDefinitionExtractor.ExtendedPossibleTypes
    //FIXME: this still does not handle optional fields validation properly for acceptUnfilledOptional == true?
    val returnType = new AvroSchemaTypeDefinitionExtractor(skippNullableFields = validationMode.acceptUnfilledOptional).typeDefinition(schema, possibleTypes)
    val restriction = new SingleSubclassRestriction {
      override def canBeSubclassOf(givenClass: typing.SingleTypingResult, superclassCandidate: typing.SingleTypingResult): Boolean = (givenClass, superclassCandidate) match {
        case (TypedObjectTypingResult(objFields, _), TypedObjectTypingResult(superFields, _)) if !validationMode.acceptRedundant =>
          objFields.keys.forall(superFields.keys.toSet.contains)
        case _ => true
      }
    }
    if (!CanBeSubclassDeterminer.canBeSubclassOf(value, returnType, restriction)) {
      Invalid(CustomNodeError("Provided value doesn't match to selected avro schema.", Some(SinkValueParamName)))
    } else {
      Valid(())
    }
  }

}
