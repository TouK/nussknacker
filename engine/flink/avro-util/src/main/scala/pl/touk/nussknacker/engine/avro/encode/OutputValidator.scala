package pl.touk.nussknacker.engine.avro.encode

import cats.data.NonEmptyList._
import cats.data.Validated.condNel
import cats.data.{Validated, ValidatedNel}
import cats.implicits._
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
    new ValidationModeAwareSubclassDeterminer(validationMode).canBeSubclassOf(value, returnType) leftMap {
      errors =>
        CustomNodeError("Provided value does not match to selected avro schema - errors:\n" +
          errors.toList.mkString, Some(SinkValueParamName))
    }
  }

  private class ValidationModeAwareSubclassDeterminer(validationMode: ValidationMode)(implicit nodeId: NodeId) extends CanBeSubclassDeterminer {
    override protected def singleCanBeSubclassOf(givenType: typing.SingleTypingResult, superclassCandidate: typing.SingleTypingResult): ValidatedNel[String, Unit] = {
      super.singleCanBeSubclassOf(givenType, superclassCandidate) combine ((givenType, superclassCandidate) match {
        case (TypedObjectTypingResult(objFields, _), TypedObjectTypingResult(superFields, _)) if !validationMode.acceptRedundant => {
          val redundantFields = objFields.keys.filterNot(superFields.keys.toSet.contains)
          condNel(redundantFields.isEmpty, (), s"The object has redundant fields: $redundantFields")
        }
        case _ => ().validNel
      })
    }
  }

}