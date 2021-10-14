package pl.touk.nussknacker.engine.avro.encode

import cats.data.NonEmptyList._
import cats.data.Validated.{Invalid, Valid, condNel}
import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType
import org.apache.avro.{Schema, SchemaCompatibility}
import org.apache.flink.formats.avro.typeutils.LogicalTypesAvroFactory
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{CanBeSubclassDeterminer, typing}
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.SinkValueParamName
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor

object OutputValidator {

  private val ValidationErrorMessageBase = "Provided value does not match selected Avro schema"

  def validateOutput(value: TypingResult, schema: Schema, validationMode: ValidationMode)(implicit nodeId: NodeId): Validated[CustomNodeError, Unit] = {
    val handleSpecifiPF = specificTypeValidation(schema)
    Option(value)
      .collect(handleSpecifiPF)
      .getOrElse(validateSchemasUsingCanBeSubclassOf(value, schema, validationMode))
  }

  private def specificTypeValidation(schema: Schema)(implicit nodeId: NodeId): PartialFunction[TypingResult, Validated[CustomNodeError, Unit]] = {
    case tc@TypedClass(klass, _) if AvroUtils.isSpecificRecord(klass) =>
      val valueSchema = LogicalTypesAvroFactory.extractAvroSpecificSchema(klass, AvroUtils.specificData)
      // checkReaderWriterCompatibility is more accurate than our validateSchemasUsingCanBeSubclassOf with given ValidationMode
      val compatibility = SchemaCompatibility.checkReaderWriterCompatibility(schema, valueSchema)
      if (compatibility.getType == SchemaCompatibilityType.COMPATIBLE) {
        Valid(Unit)
      } else {
        // ... but it return verbose message with included schemas, so we try to print more concise one
        incompatibleSchemaErrorWithPrettyMessage(schema, valueSchema)
      }
  }

  private def incompatibleSchemaErrorWithPrettyMessage(readerSchema: Schema, writerSchema: Schema)(implicit nodeId: NodeId) = {
    validateSchemasUsingCanBeSubclassOf(AvroSchemaTypeDefinitionExtractor.typeDefinition(writerSchema), readerSchema, ValidationMode.allowRedundantAndOptional).andThen { _ =>
      // in case of validateSchemasUsingCanBeSubclassOf haven't found errors, we need to return at least generic message, because we are sure that validation haven't passed
      Invalid(prepareError(Nil))
    }
  }

  private def validateSchemasUsingCanBeSubclassOf(value: TypingResult, schema: Schema, validationMode: ValidationMode)
                                                 (implicit nodeId: NodeId) = {
    val possibleTypes = AvroSchemaTypeDefinitionExtractor.ExtendedPossibleTypes
    //TODO: this still does not handle optional fields validation properly for acceptUnfilledOptional == true.
    //The optional fields types will not be validated, meaning that if e.g. String is used instead of Long, the error will not be detected during typing
    val returnType = new AvroSchemaTypeDefinitionExtractor(skipOptionalFields = validationMode.acceptUnfilledOptional).typeDefinition(schema, possibleTypes)
    new ValidationModeAwareSubclassDeterminer(validationMode).canBeSubclassOf(value, returnType).leftMap(errors => prepareError(errors.toList))
  }

  private def prepareError(errors: List[String])(implicit nodeId: NodeId) = errors match {
    case Nil => CustomNodeError(ValidationErrorMessageBase, Some(SinkValueParamName))
    case _ => CustomNodeError(errors.mkString(s"$ValidationErrorMessageBase - errors:\n", ", ", ""), Some(SinkValueParamName))
  }

  private class ValidationModeAwareSubclassDeterminer(validationMode: ValidationMode)(implicit nodeId: NodeId) extends CanBeSubclassDeterminer {
    override protected def singleCanBeSubclassOf(givenType: typing.SingleTypingResult, superclassCandidate: typing.SingleTypingResult): ValidatedNel[String, Unit] = {
      super.singleCanBeSubclassOf(givenType, superclassCandidate) combine ((givenType, superclassCandidate) match {
        case (TypedObjectTypingResult(objFields, _, _), TypedObjectTypingResult(superFields, _, _)) if !validationMode.acceptRedundant => {
          val redundantFields = objFields.keys.filterNot(superFields.keys.toSet.contains)
          condNel(redundantFields.isEmpty, (), s"The object has redundant fields: $redundantFields")
        }
        case _ => ().validNel
      })
    }
  }

}
