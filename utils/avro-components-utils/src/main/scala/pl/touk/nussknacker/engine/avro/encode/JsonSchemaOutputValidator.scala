package pl.touk.nussknacker.engine.avro.encode

import cats.data.Validated.condNel
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import org.everit.json.schema.{ObjectSchema, Schema}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.json.JsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.output._

import scala.language.implicitConversions

private[encode] case class JsonSchemaExpected(schema: Schema) extends OutputValidatorExpected {
  override def expected: String = JsonSchemaOutputValidatorPrinter.print(schema)
}

object JsonSchemaOutputValidator {
  private[encode] val SimplePath = "Value"
}

class JsonSchemaOutputValidator(validationMode: ValidationMode) extends LazyLogging {

  import JsonSchemaOutputValidator._

  import scala.collection.JavaConverters._

  private val valid = Validated.Valid(())

  /**
   * Currently we support only basic json-schema configurations. See {@link pl.touk.nussknacker.engine.json.JsonSchemaTypeDefinitionExtractor}
   */
  def validateTypingResultToSchema(typingResult: TypingResult, parentSchema: Schema)(implicit nodeId: NodeId): ValidatedNel[OutputValidatorError, Unit] =
    validateTypingResult(typingResult, parentSchema, None)

  //todo: add support for: unions, enums, nested types, logical types
  final private def validateTypingResult(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
    (typingResult, schema) match {
      case (typingResult: TypedObjectTypingResult, s: ObjectSchema) =>
        validateRecordSchema(typingResult, s, path)
      case (_@TypedNull, _) if !schema.isNullable =>
        invalid(typingResult, schema, path)
      case (_@TypedNull, _) if schema.isNullable => valid
      case (_, _) => canBeSubclassOf(typingResult, schema, path)
    }
  }

  private def validateRecordSchema(typingResult: TypedObjectTypingResult, schema: ObjectSchema, path: Option[String]): Validated[NonEmptyList[OutputValidatorError], Unit] = {
    val schemaFields = schema.getPropertySchemas.asScala
    def prepareFields(fields: Set[String]) = fields.flatMap(buildPath(_, path))

    val requiredFieldsValidation = {
      val requiredFieldNames = if (validationMode == ValidationMode.strict) {
        schemaFields.keys
      } else {
        schemaFields.filterNot(_._2.hasDefaultValue).keys
      }
      {
        val missingFields = requiredFieldNames.filterNot(typingResult.fields.contains).toList.sorted.toSet
        condNel(missingFields.isEmpty, (), OutputValidatorMissingFieldsError(prepareFields(missingFields)))
      }
    }
    val schemaFieldsValidation = {
      val fieldsToValidate: Map[String, TypingResult] = typingResult.fields.filterKeys(schemaFields.contains)
      fieldsToValidate.flatMap { case (key, value) =>
        val fieldPath = buildPath(key, path)
        schemaFields.get(key).map(f => validateTypingResult(value, f, fieldPath))
      }.foldLeft[ValidatedNel[OutputValidatorError, Unit]](().validNel)((a, b) => a combine b)
    }
    val redundantFieldsVadlidation = {
      val redundantFields = typingResult.fields.keySet.diff(schemaFields.keySet)
      condNel(redundantFields.isEmpty || validationMode != ValidationMode.strict, (), OutputValidatorRedundantFieldsError(prepareFields(redundantFields)))
    }

    requiredFieldsValidation combine schemaFieldsValidation combine redundantFieldsVadlidation
  }

  /**
   * TODO: Consider verification class instead of using .canBeSubclassOf from Typing - we want to avoid:
   * * Unknown.canBeSubclassOf(Any) => true
   * * Long.canBeSubclassOf(Integer) => true
   * Should we use strict verification at json?
   */
  private def canBeSubclassOf(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
    val schemaAsTypedResult = JsonSchemaTypeDefinitionExtractor.typeDefinition(schema)
    condNel(typingResult.canBeSubclassOf(schemaAsTypedResult), (),
      OutputValidatorTypeError(path.getOrElse(SimplePath), typingResult, JsonSchemaExpected(schema))
    )
  }

  private def invalid(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorTypeError, Nothing] =
    Validated.invalidNel(typeError(typingResult, schema, path))

  private def typeError(typingResult: TypingResult, schema: Schema, path: Option[String]) =
    OutputValidatorTypeError(path.getOrElse(SimplePath), typingResult, JsonSchemaExpected(schema))

  private def buildPath(key: String, path: Option[String], useIndexer: Boolean = false) = Some(
    path.map(p => if (useIndexer) s"$p[$key]" else s"$p.$key").getOrElse(key)
  )
}
