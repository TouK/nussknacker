package pl.touk.nussknacker.engine.json.encode

import cats.data.Validated.condNel
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.ClassUtils
import org.everit.json.schema.{EmptySchema, NumberSchema, ObjectSchema, ReferenceSchema, Schema, ValidationException}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.json.SwaggerBasedJsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.json.JsonSchemaImplicits._
import pl.touk.nussknacker.engine.util.output.{OutputValidatorError, _}

import scala.language.implicitConversions

private[encode] case class JsonSchemaExpected(schema: Schema, rootSchema: Schema) extends OutputValidatorExpected {
  override def expected: String = new JsonSchemaOutputValidatorPrinter(rootSchema).print(schema)
}

private[encode] case class NumberSchemaRangeExpected(schema: NumberSchema) extends OutputValidatorExpected {

  override def expected: String = (minimumValue, maximumValue) match {
    case (Some(min), Some(max)) => s"between $min and $max"
    case (Some(min), None)      => s"greater than or equal to $min"
    case (None, Some(max))      => s"less than or equal to $max"
    case _ => throw new IllegalArgumentException("Schema does not contain integer range. This should be unreachable.")
  }

  private val minimumValue = List(
    Option(schema.getMinimum).map(x => BigInt(s"$x")),
    Option(schema.getExclusiveMinimumLimit).map(x => BigInt(s"$x") + 1)
  ).flatten.sorted.headOption

  private val maximumValue = List(
    Option(schema.getMaximum).map(x => BigInt(s"$x")),
    Option(schema.getExclusiveMaximumLimit).map(x => BigInt(s"$x") - 1)
  ).flatten.sorted(Ordering.BigInt.reverse).headOption

}

object JsonSchemaOutputValidator {

  private implicit class RichTypedClass(t: TypedClass) {

    val representsMapWithStringKeys: Boolean = {
      t.klass == classOf[java.util.Map[_, _]] && t.params.size == 2 && t.params.head == Typed.typedClass[String]
    }

  }

  private val BigDecimalClass = classOf[java.math.BigDecimal]
  private val NumberClass     = classOf[java.lang.Number]
}

// root schema is a container for eventual ref schemas - in particular it can be the same schema as outputSchema
class JsonSchemaOutputValidator(validationMode: ValidationMode) extends LazyLogging {

  import JsonSchemaOutputValidator._

  import scala.jdk.CollectionConverters._

  private val valid = Validated.Valid(())

  /**
    * To see what's we currently supporting see SwaggerBasedJsonSchemaTypeDefinitionExtractor as well
    */
  def validate(
      typingResult: TypingResult,
      outputSchema: Schema,
      rootSchema: Option[Schema] = None
  ): ValidatedNel[OutputValidatorError, Unit] =
    validateTypingResult(typingResult, outputSchema, rootSchema.getOrElse(outputSchema), None)

  // TODO: add support for: enums, logical types
  final private def validateTypingResult(
      typingResult: TypingResult,
      schema: Schema,
      rootSchema: Schema,
      path: Option[String]
  ): ValidatedNel[OutputValidatorError, Unit] = {
    (typingResult, schema) match {
      case (_, referenceSchema: ReferenceSchema) =>
        validateTypingResult(typingResult, referenceSchema.getReferredSchema, rootSchema, path)
      case (_, _: EmptySchema)    => valid
      case (Unknown, _)           => validateUnknownInputType(schema, rootSchema, path)
      case (union: TypedUnion, _) => validateUnionInputType(union, schema, rootSchema, path)
      case (tc: TypedClass, s: ObjectSchema) if tc.representsMapWithStringKeys =>
        validateMapInputType(tc, tc.params.tail.head, s, rootSchema, path)
      case (typingResult: TypedObjectTypingResult, s: ObjectSchema) =>
        validateRecordInputType(typingResult, s, rootSchema, path)
      case (typed: TypedObjectWithValue, s: NumberSchema)
          if ClassUtils.isAssignable(typed.underlying.runtimeObjType.primitiveClass, classOf[Number], true) =>
        validateValueAgainstNumberSchema(s, typed, path)
      case (_, _) => canBeAssignedTo(typingResult, schema, rootSchema, path)
    }
  }

  private def validateValueAgainstNumberSchema(
      schema: NumberSchema,
      typeWithValue: TypedObjectWithValue,
      path: Option[String]
  ): ValidatedNel[OutputValidatorError, Unit] =
    Validated
      .catchOnly[ValidationException] {
        schema.validate(typeWithValue.value)
      }
      .leftMap(_ =>
        NonEmptyList.one(OutputValidatorRangeTypeError(path, typeWithValue, NumberSchemaRangeExpected(schema)))
      )

  private def validateUnknownInputType(
      schema: Schema,
      rootSchema: Schema,
      path: Option[String]
  ): ValidatedNel[OutputValidatorError, Unit] = {
    validationMode match {
      case ValidationMode.lax    => valid
      case ValidationMode.strict => invalid(Unknown, schema, rootSchema, path)
      case validationMode        => throw new IllegalStateException(s"Unsupported validation mode $validationMode")
    }
  }

  private def validateUnionInputType(union: TypedUnion, schema: Schema, rootSchema: Schema, path: Option[String]) = {
    if (validationMode == ValidationMode.strict && !union.possibleTypes.forall(
        validateTypingResult(_, schema, rootSchema, path).isValid
      ))
      invalid(union, schema, rootSchema, path)
    else if (validationMode == ValidationMode.lax && !union.possibleTypes.exists(
        validateTypingResult(_, schema, rootSchema, path).isValid
      ))
      invalid(union, schema, rootSchema, path)
    else
      valid
  }

  private def validateMapInputType(
      mapTypedClass: TypedClass,
      mapValuesTypingResult: TypingResult,
      schema: ObjectSchema,
      rootSchema: Schema,
      path: Option[String]
  ): ValidatedNel[OutputValidatorError, Unit] = {
    if (validationMode == ValidationMode.strict) {
      validateMapInputTypeStrict(mapTypedClass, mapValuesTypingResult, schema, rootSchema, path)
    } else {
      validateMapInputTypeLax(mapValuesTypingResult, schema, rootSchema, path)
    }
  }

  private def validateMapInputTypeStrict(
      mapTypedClass: TypedClass,
      mapValuesTypingResult: TypingResult,
      schema: ObjectSchema,
      rootSchema: Schema,
      path: Option[String]
  ): ValidatedNel[OutputValidatorError, Unit] = {
    if (hasDefinedExplicitProps(schema) || schema.hasPatternProperties) {
      invalid(mapTypedClass, schema, rootSchema, path)
    } else if (schema.acceptsEverythingAsAdditionalProperty) {
      valid
    } else {
      validateTypingResult(
        mapValuesTypingResult,
        schema.getSchemaOfAdditionalProperties,
        rootSchema,
        buildFieldPath("value", path)
      )
    }
  }

  private def hasDefinedExplicitProps(schema: ObjectSchema) = {
    !schema.getPropertySchemas.isEmpty
  }

  private def validateMapInputTypeLax(
      mapValuesTypingResult: TypingResult,
      schema: ObjectSchema,
      rootSchema: Schema,
      path: Option[String]
  ): ValidatedNel[OutputValidatorTypeError, Unit] = {
    if (isPossibleToProvideValidInputUsingMapValueType(schema, mapValuesTypingResult, rootSchema)) {
      valid
    } else {
      invalid(mapValuesTypingResult, schema.getSchemaOfAdditionalProperties, rootSchema, buildFieldPath("value", path))
    }
  }

  private def isPossibleToProvideValidInputUsingMapValueType(
      objectSchema: ObjectSchema,
      mapValueType: TypingResult,
      rootSchema: Schema
  ) = {
    val requiredPropertiesSchemas = objectSchema.requiredPropertiesSchemas
    if (requiredPropertiesSchemas.nonEmpty) {
      allSchemasMatchesType(requiredPropertiesSchemas.values.toList, mapValueType, rootSchema)
    } else if (objectSchema.acceptsEverythingAsAdditionalProperty) {
      true
    } else {
      val explicitPropsSchemas = objectSchema.getPropertySchemas.asScala.values.toList
      val patternPropsSchemas  = objectSchema.patternProperties.values.toList
      val additionalPropertiesSchema =
        if (objectSchema.permitsAdditionalProperties()) List(objectSchema.getSchemaOfAdditionalProperties) else List()
      val schemasToCheck = additionalPropertiesSchema ++ patternPropsSchemas ++ explicitPropsSchemas
      atLeastOneSchemaMatchesType(schemasToCheck, mapValueType, rootSchema)
    }
  }

  private def allSchemasMatchesType(
      schemasToCheck: List[Schema],
      typingResult: TypingResult,
      rootSchema: Schema
  ): Boolean = {
    !schemasToCheck.exists(schema => validateTypingResult(typingResult, schema, rootSchema, None).isInvalid)
  }

  private def atLeastOneSchemaMatchesType(
      schemasToCheck: List[Schema],
      typingResult: TypingResult,
      rootSchema: Schema
  ): Boolean = {
    schemasToCheck.exists(schema => validateTypingResult(typingResult, schema, rootSchema, None).isValid)
  }

  private def validateRecordInputType(
      typingResult: TypedObjectTypingResult,
      schema: ObjectSchema,
      rootSchema: Schema,
      path: Option[String]
  ): ValidatedNel[OutputValidatorError, Unit] = {
    val explicitProps          = schema.getPropertySchemas.asScala.toMap
    val requiredProps          = schema.getRequiredProperties.asScala.toSet
    val schemaFieldsValidation = validateRecordExplicitProperties(typingResult, explicitProps, rootSchema, path)

    val requiredPropsV  = validateRecordRequiredProps(typingResult, explicitProps, requiredProps, path)
    val redundantPropsV = validateRecordRedundantProps(typingResult, schema, explicitProps, path)
    val (patternPropsV, inputFieldsMatchedByPatternProps) =
      validateRecordPatternProps(typingResult, schema, rootSchema, path)
    val foundAdditionalProps =
      findRecordAdditionalProps(typingResult, explicitProps.keySet, inputFieldsMatchedByPatternProps)
    val additionalPropsV = validateRecordAdditionalProps(schema, path, foundAdditionalProps, rootSchema)

    requiredPropsV
      .combine(schemaFieldsValidation)
      .combine(redundantPropsV)
      .combine(patternPropsV)
      .combine(additionalPropsV)
  }

  private def validateRecordRequiredProps(
      typingResult: TypedObjectTypingResult,
      explicitPropsSchemas: Map[String, Schema],
      requiredProps: Set[String],
      path: Option[String]
  ): ValidatedNel[OutputValidatorMissingFieldsError, Unit] = {
    val requiredPropsNames = if (validationMode == ValidationMode.strict) {
      explicitPropsSchemas.keys.toSet
    } else {
      requiredProps
    }
    val missingProps = requiredPropsNames.filterNot(typingResult.fields.contains)
    condNel(missingProps.isEmpty, (), OutputValidatorMissingFieldsError(buildFieldsPaths(missingProps, path)))
  }

  private def validateRecordExplicitProperties(
      typingResult: TypedObjectTypingResult,
      schemaFields: Map[String, Schema],
      rootSchema: Schema,
      path: Option[String]
  ): ValidatedNel[OutputValidatorError, Unit] = {
    validateFieldsType(typingResult.fields.filterKeysNow(schemaFields.contains), schemaFields, rootSchema, path)
  }

  private def validateRecordRedundantProps(
      typingResult: TypedObjectTypingResult,
      schema: ObjectSchema,
      schemaFields: Map[String, Schema],
      path: Option[String]
  ): ValidatedNel[OutputValidatorRedundantFieldsError, Unit] = {
    val redundantFields = typingResult.fields.keySet.diff(schemaFields.keySet)
    condNel(
      redundantFields.isEmpty || schema.permitsAdditionalProperties() || validationMode == ValidationMode.lax,
      (),
      OutputValidatorRedundantFieldsError(buildFieldsPaths(redundantFields, path))
    )
  }

  private def validateRecordPatternProps(
      typingResult: TypedObjectTypingResult,
      schema: ObjectSchema,
      rootSchema: Schema,
      path: Option[String]
  ): (Validated[NonEmptyList[OutputValidatorError], Unit], Set[String]) = {
    val fieldsWithMatchedPatternsProperties = typingResult.fields.toList
      .map { case (fieldName, _) =>
        fieldName -> schema.patternProperties.filterKeysNow(p => p.asPredicate().test(fieldName)).values.toList
      }
      .filter { case (_, schemas) => schemas.nonEmpty }

    val validation = fieldsWithMatchedPatternsProperties
      .flatMap { case (fieldName, schemas) =>
        schemas.map(schema => validateTypingResult(typingResult.fields(fieldName), schema, rootSchema, path))
      }
      .sequence
      .map(_ => (): Unit)
    (validation, fieldsWithMatchedPatternsProperties.map { case (name, _) => name }.toSet)
  }

  private def findRecordAdditionalProps(
      typingResult: TypedObjectTypingResult,
      schemaFields: Set[String],
      propertiesMatchedByPatternProperties: Set[String]
  ): Map[String, TypingResult] = {
    typingResult.fields.filterKeysNow(k =>
      !schemaFields.contains(k) && !propertiesMatchedByPatternProperties.contains(k)
    )
  }

  private def validateRecordAdditionalProps(
      schema: ObjectSchema,
      path: Option[String],
      additionalFieldsToValidate: Map[String, TypingResult],
      rootSchema: Schema
  ): ValidatedNel[OutputValidatorError, Unit] = {
    if (additionalFieldsToValidate.isEmpty || schema.getSchemaOfAdditionalProperties == null) {
      valid
    } else {
      validateFieldsType(
        additionalFieldsToValidate,
        additionalFieldsToValidate.mapValuesNow(_ => schema.getSchemaOfAdditionalProperties),
        rootSchema,
        path
      )
    }
  }

  private def validateFieldsType(
      fieldsToValidate: Map[String, TypingResult],
      schemas: Map[String, Schema],
      rootSchema: Schema,
      path: Option[String]
  ): ValidatedNel[OutputValidatorError, Unit] = {
    fieldsToValidate
      .flatMap { case (key, value) =>
        val fieldPath = buildFieldPath(key, path)
        schemas.get(key).map(fieldSchema => validateTypingResult(value, fieldSchema, rootSchema, fieldPath))
      }
      .foldLeft[ValidatedNel[OutputValidatorError, Unit]](().validNel)((a, b) => a combine b)
  }

  /**
   * TODO: Consider verification class instead of using .canBeSubclassOf from Typing - we want to avoid:
   * * Unknown.canBeSubclassOf(Any) => true
   * Should we use strict verification at json?
   */
  private def canBeAssignedTo(
      typingResult: TypingResult,
      schema: Schema,
      rootSchema: Schema,
      path: Option[String]
  ): ValidatedNel[OutputValidatorError, Unit] = {
    val schemaAsTypedResult =
      SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema, Some(rootSchema)).typingResult

    (schemaAsTypedResult, typingResult) match {
      case (schema @ TypedClass(_, Nil), typing @ TypedClass(_, Nil))
          if canBeAssignedToWithBigDecimalFallback(typing.primitiveClass, schema.primitiveClass) =>
        valid
      case (TypedClass(_, Nil), TypedClass(_, Nil)) => invalid(typingResult, schema, rootSchema, path)
      case _ =>
        condNel(
          typingResult.canBeConvertedTo(schemaAsTypedResult),
          (),
          OutputValidatorTypeError(path, typingResult, JsonSchemaExpected(schema, rootSchema))
        )
    }
  }

  /**
   * we are mapping json schema types to java strict class hierarchy and due to dual definition of `number` type (as integer | double) in json schema we treat BigDecimal class
   * as a fallback type, which can accommodate all other numeric type.
   */
  private def canBeAssignedToWithBigDecimalFallback(typingClass: Class[_], schemaClass: Class[_]): Boolean =
    ClassUtils.isAssignable(typingClass, withBigDecimalAsNumber(schemaClass), true)

  private def withBigDecimalAsNumber(clazz: Class[_]): Class[_] =
    clazz match {
      case `BigDecimalClass` => NumberClass
      case _                 => clazz
    }

  private def invalid(
      typingResult: TypingResult,
      schema: Schema,
      rootSchema: Schema,
      path: Option[String]
  ): ValidatedNel[OutputValidatorTypeError, Nothing] =
    Validated.invalidNel(typeError(typingResult, schema, rootSchema, path))

  private def typeError(typingResult: TypingResult, schema: Schema, rootSchema: Schema, path: Option[String]) =
    OutputValidatorTypeError(path, typingResult, JsonSchemaExpected(schema, rootSchema))

  private def buildFieldsPaths(fields: Set[String], path: Option[String]) = fields.flatMap(buildFieldPath(_, path))

  private def buildFieldPath(key: String, path: Option[String], useIndexer: Boolean = false) = Some(
    path.map(p => if (useIndexer) s"$p[$key]" else s"$p.$key").getOrElse(key)
  )

}
