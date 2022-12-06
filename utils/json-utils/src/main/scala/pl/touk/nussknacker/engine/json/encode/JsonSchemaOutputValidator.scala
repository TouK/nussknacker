package pl.touk.nussknacker.engine.json.encode

import cats.data.Validated.{Valid, condNel}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import org.everit.json.schema.{ObjectSchema, Schema}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.json.SwaggerBasedJsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.output._
import pl.touk.nussknacker.engine.util.json.JsonSchemaImplicits._

import scala.language.implicitConversions

private[encode] case class JsonSchemaExpected(schema: Schema) extends OutputValidatorExpected {
  override def expected: String = JsonSchemaOutputValidatorPrinter.print(schema)
}

object JsonSchemaOutputValidator {

  private implicit class RichTypedClass(t: TypedClass) {
    val representsMapWithStringKeys: Boolean = {
      t.klass == classOf[java.util.Map[_, _]] && t.params.size == 2 && t.params.head == Typed.typedClass[String]
    }
  }
}

class JsonSchemaOutputValidator(validationMode: ValidationMode) extends LazyLogging {

  import JsonSchemaOutputValidator._

  import scala.collection.JavaConverters._

  private val valid = Validated.Valid(())

  /**
   * To see what's we currently supporting see SwaggerBasedJsonSchemaTypeDefinitionExtractor as well
   */
  def validateTypingResultAgainstSchema(typingResult: TypingResult, schema: Schema): ValidatedNel[OutputValidatorError, Unit] =
    validateTypingResult(typingResult, schema, None)

  //todo: add support for: unions, enums, nested types, logical types
  final private def validateTypingResult(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
    (typingResult, schema) match {
      case (Unknown, _) if validationMode == ValidationMode.lax => valid
      case (Unknown, _) if validationMode == ValidationMode.strict => invalid(typingResult, schema, path)
      case (union: TypedUnion, _) =>
        validateUnionInput(union, schema, path)
      case (typingResult: TypedObjectTypingResult, s: ObjectSchema) if s.hasOnlyAdditionalProperties => validateMapSchema(path, s, typingResult.fields.toList: _*)
      case (tc: TypedClass, s: ObjectSchema) if s.hasOnlyAdditionalProperties && tc.representsMapWithStringKeys => validateMapSchema(path, s, ("value", tc.params.tail.head))
      case (typingResult: TypedObjectTypingResult, s: ObjectSchema) if !s.hasOnlyAdditionalProperties => validateRecordSchema(typingResult, s, path)
      case (_, _) => canBeSubclassOf(typingResult, schema, path)
    }
  }

  private def validateMapSchema(path: Option[String], mapSchema: ObjectSchema, fields: (String, TypingResult)*): ValidatedNel[OutputValidatorError, Unit] = {
    if (mapSchema.getSchemaOfAdditionalProperties == null)
      valid
    else
      fields.map {
        case (fName, fType) => validateTypingResult(fType, mapSchema.getSchemaOfAdditionalProperties, buildPath(fName, path))
      }.reduceOption(_ combine _).getOrElse(Valid(()))
  }

  private def validateUnionInput(union: TypedUnion, schema: Schema, path: Option[String]) = {
    if (validationMode == ValidationMode.strict && !union.possibleTypes.forall(validateTypingResult(_, schema, path).isValid))
      invalid(union, schema, path)
    else if (validationMode == ValidationMode.lax && !union.possibleTypes.exists(validateTypingResult(_, schema, path).isValid))
      invalid(union, schema, path)
    else
      valid
  }

  private def validateRecordSchema(typingResult: TypedObjectTypingResult, schema: ObjectSchema, path: Option[String]): Validated[NonEmptyList[OutputValidatorError], Unit] = {
    val schemaFields: Map[String, Schema] = schema.getPropertySchemas.asScala.toMap
    def prepareFields(fields: Set[String]) = fields.flatMap(buildPath(_, path))

    def validateFieldsType(schemas: Map[String, Schema], fieldsToValidate: Map[String, TypingResult]) = {
      fieldsToValidate.flatMap { case (key, value) =>
        val fieldPath = buildPath(key, path)
        schemas.get(key).map(f => validateTypingResult(value, f, fieldPath))
      }.foldLeft[ValidatedNel[OutputValidatorError, Unit]](().validNel)((a, b) => a combine b)
    }

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

    val schemaFieldsValidation = validateFieldsType(schemaFields, typingResult.fields.filterKeys(schemaFields.contains))

    val redundantFieldsValidation = {
      val redundantFields = typingResult.fields.keySet.diff(schemaFields.keySet)
      condNel(redundantFields.isEmpty || schema.permitsAdditionalProperties(), (), OutputValidatorRedundantFieldsError(prepareFields(redundantFields)))
    }

    val additionalFieldsValidation = {
      val additionalFields = typingResult.fields.filterKeys(k => !schemaFields.keySet.contains(k))
      if(additionalFields.isEmpty || schema.getSchemaOfAdditionalProperties == null)
        valid
       else
        validateFieldsType(additionalFields.mapValues(_ => schema.getSchemaOfAdditionalProperties), additionalFields)
    }

    requiredFieldsValidation combine schemaFieldsValidation combine redundantFieldsValidation combine additionalFieldsValidation
  }

  /**
   * TODO: Consider verification class instead of using .canBeSubclassOf from Typing - we want to avoid:
   * * Unknown.canBeSubclassOf(Any) => true
   * * Long.canBeSubclassOf(Integer) => true
   * Should we use strict verification at json?
   */
  private def canBeSubclassOf(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
    val schemaAsTypedResult = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema).typingResult
    condNel(typingResult.canBeSubclassOf(schemaAsTypedResult), (),
      OutputValidatorTypeError(path, typingResult, JsonSchemaExpected(schema))
    )
  }

  private def invalid(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorTypeError, Nothing] =
    Validated.invalidNel(typeError(typingResult, schema, path))

  private def typeError(typingResult: TypingResult, schema: Schema, path: Option[String]) =
    OutputValidatorTypeError(path, typingResult, JsonSchemaExpected(schema))

  private def buildPath(key: String, path: Option[String], useIndexer: Boolean = false) = Some(
    path.map(p => if (useIndexer) s"$p[$key]" else s"$p.$key").getOrElse(key)
  )
}
