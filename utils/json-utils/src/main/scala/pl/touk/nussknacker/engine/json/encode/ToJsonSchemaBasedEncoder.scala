package pl.touk.nussknacker.engine.json.encode

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.toTraverseOps
import io.circe.Json
import org.everit.json.schema._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.util.json._

import java.time.{LocalDate, OffsetDateTime, OffsetTime, ZonedDateTime}
import java.util.ServiceLoader
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class ToJsonSchemaBasedEncoder(validationMode: ValidationMode) {

  import pl.touk.nussknacker.engine.util.json.JsonSchemaImplicits._

  private val classLoader = this.getClass.getClassLoader
  private val jsonEncoder = ToJsonEncoder(failOnUnknown = false, this.getClass.getClassLoader)

  private val optionalEncoders = ServiceLoader
    .load(classOf[ToJsonSchemaBasedEncoderCustomisation], classLoader)
    .asScala
    .map(_.encoder(this.encodeBasedOnSchema))

  private val highPriority: PartialFunction[EncodeInput, EncodeOutput] = Map()

  final def encodeOrError(value: Any, schema: Schema): Json = {
    encodeWithJsonValidation(value, schema).valueOr(errors => throw new RuntimeException(errors.toList.mkString(",")))
  }

  private[encode] def encodeWithJsonValidation(
      value: Any,
      schema: Schema,
      fieldName: Option[String] = None
  ): EncodeOutput =
    encode(value, schema, fieldName).andThen { result =>
      val json = JsonSchemaUtils.circeToJson(result)
      schema
        .validateData(json)
        .leftMap(errorMsg => NonEmptyList.of(errorMsg))
        .map(_ =>
          result
        ) // we return here base json (e.g. without defaults - there is no need to put these data as output, because json still is valid)
    }

  private def encode(value: Any, schema: Schema, fieldName: Option[String] = None): EncodeOutput = {
    optionalEncoders
      .foldLeft(highPriority)(_.orElse(_))
      .applyOrElse[EncodeInput, EncodeOutput]((value, schema, fieldName), encodeBasedOnSchema)
  }

  def encodeBasedOnSchema(input: EncodeInput): EncodeOutput = {
    val (value, schema, fieldName) = input
    (schema, value) match {
      case (_: EmptySchema, _)                                                     => Valid(jsonEncoder.encode(value))
      case (schema: ObjectSchema, map: scala.collection.Map[String @unchecked, _]) => encodeObject(map.toMap, schema)
      case (schema: ObjectSchema, map: java.util.Map[String @unchecked, _]) => encodeObject(map.asScala.toMap, schema)
      case (schema: ArraySchema, value: Iterable[_])                        => encodeCollection(value, schema)
      case (schema: ArraySchema, value: java.util.Collection[_])            => encodeCollection(value.toArray, schema)
      case (cs: CombinedSchema, value) =>
        cs.getSubschemas.asScala.view
          .map(encodeBasedOnSchema(value, _, fieldName))
          .find(_.isValid)
          .getOrElse(error(value, cs.toString, fieldName))
      case (schema: StringSchema, value: Any) => encodeStringSchema(schema, value, fieldName)
      case (ref: ReferenceSchema, value: Any) => encodeBasedOnSchema(value, ref.getReferredSchema, fieldName)
      case (nm: NumberSchema, value: Any) if nm.requiresInteger() => encodeIntegerSchema(value, nm, fieldName)
      case (_: NumberSchema, value: Long)                         => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Double)                       => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Float)                        => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Int)                          => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: java.math.BigDecimal)         => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: java.math.BigInteger)         => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Number)                       => Valid(jsonEncoder.encode(value.doubleValue()))
      case (_: BooleanSchema, value: Boolean)                     => Valid(Json.fromBoolean(value))
      case (_: EnumSchema, value: Any)                            => Valid(jsonEncoder.encode(value))
      case (_: NullSchema, null)                                  => Valid(Json.Null)
      case (_: NullSchema, None)                                  => Valid(Json.Null)
      case (_, null)                                              => error(null, schema.toString, fieldName)
      case (_, _)                                                 => error(value, schema.toString, fieldName)
    }
  }

  private def encodeObject(fields: Map[String, _], parentSchema: ObjectSchema): EncodeOutput = {
    (fields.keys.toList ++ parentSchema.getPropertySchemas.keySet.asScala.toList).distinct
      .map { key =>
        val schema = Option(parentSchema.getPropertySchemas.get(key))
        val value  = fields.get(key)
        ObjectField(key, value, schema, parentSchema)
      } // we do collect here because we want to do own pre-validation with human readably message (consider doing encode and at the end schema.validate instead of this)
      .collect(encodeFieldWithSchema.orElse(encodeFieldWithoutSchema))
      .sequence
      .map { values => Json.fromFields(values) }
  }

  private def encodeFieldWithSchema: PartialFunction[ObjectField, WithError[(String, Json)]] = {
    case ObjectField(fieldName, Some(null), Some(schema), _) if schema.isNullableSchema =>
      Valid((fieldName, Json.Null))
    case ObjectField(fieldName, None, Some(schema), parentSchema)
        if parentSchema.getRequiredProperties.contains(fieldName) && schema.isNullableSchema =>
      // We implicitly add a missing null because for a user it will be difficult to handle that using available in Nussknacker tools
      Valid((fieldName, Json.Null))
    case ObjectField(fieldName, Some(null) | None, Some(schema), parentSchema)
        if parentSchema.getRequiredProperties.contains(fieldName) && schema.hasDefaultValue =>
      // We implicitly add a missing default value because for a user it will be difficult to handle that using available in Nussknacker tools
      encode(schema.getDefaultValue, schema, Some(fieldName)).map(fieldName -> _)
    case ObjectField(fieldName, None, Some(_), parentSchema)
        if parentSchema.getRequiredProperties.contains(fieldName) =>
      error(s"Missing property: $fieldName for schema: $parentSchema.")
    // We implicitly remove redundant null because for a user it will be difficult to handle that using available in Nussknacker tools
    case ObjectField(fieldName, Some(value), Some(schema), parentSchema)
        if parentSchema.getRequiredProperties.contains(fieldName) || value != null =>
      encode(value, schema, Some(fieldName)).map(fieldName -> _)
  }

  private def encodeFieldWithoutSchema: PartialFunction[ObjectField, WithError[(String, Json)]] = {
    // Note: if a field matches both properties and pattern property, we try to encode the field using its property schema
    // instead of encoding with all of: property and pattern property schemas.
    case PatternPropertySchema(schema, ObjectField(fieldName, Some(value), _, _)) =>
      encode(value, schema, Some(fieldName)).map(fieldName -> _)
    case ObjectField(fieldName, _, None, parentSchema)
        if !parentSchema.permitsAdditionalProperties() && validationMode != ValidationMode.lax =>
      error(s"Not expected field with name: $fieldName for schema: $parentSchema.")
    case ObjectField(fieldName, Some(value), None, parentSchema) if parentSchema.permitsAdditionalProperties =>
      Option(parentSchema.getSchemaOfAdditionalProperties) match {
        case Some(additionalPropertySchema) => encode(value, additionalPropertySchema).map(fieldName -> _)
        case None                           => Valid(jsonEncoder.encode(value)).map(fieldName -> _)
      }
  }

  private def encodeCollection(collection: Iterable[_], schema: ArraySchema): EncodeOutput = {
    collection.map(el => encode(el, schema.getAllItemSchema)).toList.sequence.map(l => Json.fromValues(l))
  }

  private def encodeStringSchema(
      schema: StringSchema,
      value: Any,
      fieldName: Option[String] = None
  ): Validated[NonEmptyList[String], Json] = {
    (schema.getFormatValidator.formatName(), value) match {
      case ("date-time", zdt: ZonedDateTime)  => Valid(jsonEncoder.encode(zdt))
      case ("date-time", odt: OffsetDateTime) => Valid(jsonEncoder.encode(odt))
      case ("date-time", _: Any)              => error(value, schema.toString, fieldName)
      case ("date", ldt: LocalDate)           => Valid(jsonEncoder.encode(ldt))
      case ("date", _: Any)                   => error(value, schema.toString, fieldName)
      case ("time", ot: OffsetTime)           => Valid(jsonEncoder.encode(ot))
      case ("time", _: Any)                   => error(value, schema.toString, fieldName)
      case ("unnamed-format", _: String)      => Valid(jsonEncoder.encode(value))
      case _                                  => error(value, schema.toString, fieldName)
    }
  }

  /**
   * We want to be consistent with json schema specification: https://json-schema.org/understanding-json-schema/reference/numeric.html#integer
   * and allow to pass e.g. `1.0` for integer schema. Consequently we have to try to convert floating point numbers to integer because
   * Everit validation throws exception for floating numbers with `.0`.
   */
  private def encodeIntegerSchema(
      value: Any,
      schema: NumberSchema,
      fieldName: Option[String] = None
  ): Validated[NonEmptyList[String], Json] = {
    // TODO: Right now wy try to convert BigDecimal to long but we should addictive conversion from schema.getMinimum and schema.getMaximum
    def encodeBigDecimalToIntegerSchema(bigDecimalValue: BigDecimal): Validated[NonEmptyList[String], Json] = Try(
      bigDecimalValue.toLongExact
    ) match {
      case Failure(_) => error(s"value '$value' is not an integer.", fieldName)
      case Success(v) => Valid(jsonEncoder.encode(v))
    }

    value match {
      case i: Int                    => Valid(jsonEncoder.encode(i))
      case l: Long                   => Valid(jsonEncoder.encode(l))
      case d: Double                 => encodeBigDecimalToIntegerSchema(BigDecimal(d))
      case f: Float                  => encodeBigDecimalToIntegerSchema(BigDecimal(f.toDouble))
      case bd: java.math.BigDecimal  => encodeBigDecimalToIntegerSchema(bd)
      case bd: scala.math.BigDecimal => encodeBigDecimalToIntegerSchema(bd)
      case bi: scala.math.BigInt     => encodeBigDecimalToIntegerSchema(BigDecimal(bi))
      case bi: java.math.BigInteger  => encodeBigDecimalToIntegerSchema(BigDecimal(bi))
      case nm: Number                => encodeBigDecimalToIntegerSchema(BigDecimal(nm.toString))
      case _                         => error(value, schema.toString, fieldName)
    }
  }

  private def error(str: String): Invalid[NonEmptyList[String]] = Invalid(NonEmptyList.of(str))

  private def error(msg: String, fieldName: Option[String]): Invalid[NonEmptyList[String]] =
    Invalid(NonEmptyList.of(s"Field${fieldName.map(f => s": '$f'").getOrElse("")} $msg"))

  private def error(runtimeObject: Any, schema: String, fieldName: Option[String]): Invalid[NonEmptyList[String]] = {
    val runtimeType = Typed.fromInstance(runtimeObject)
    Invalid(
      NonEmptyList.of(
        s"Not expected type: ${runtimeType.withoutValue.display} for field${fieldName.map(f => s": '$f'").getOrElse("")} with schema: $schema."
      )
    )
  }

  private object PatternPropertySchema {

    def unapply(objectField: ObjectField): Option[(Schema, ObjectField)] = {
      findPatternPropertySchema(objectField.name, objectField.parentSchema)
        .map((_, objectField))
    }

    private def findPatternPropertySchema(fieldName: String, parentSchema: ObjectSchema): Option[Schema] = {
      parentSchema.patternProperties.collectFirst {
        case (pattern, schema) if pattern.asPredicate().test(fieldName) => schema
      }
    }

  }

  private case class ObjectField(name: String, value: Option[_], schema: Option[Schema], parentSchema: ObjectSchema)

}
