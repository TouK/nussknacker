package pl.touk.nussknacker.engine.json.encode

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.toTraverseOps
import io.circe.Json
import org.everit.json.schema.{ArraySchema, BooleanSchema, CombinedSchema, EnumSchema, NullSchema, NumberSchema, ObjectSchema, Schema, StringSchema}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.util.json.{BestEffortJsonEncoder, EncodeInput, EncodeOutput, ToJsonBasedOnSchemaEncoder}

import java.time.{LocalDate, OffsetDateTime, OffsetTime, ZonedDateTime}
import java.util.ServiceLoader
import scala.collection.convert.ImplicitConversions.`map AsScala`
import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter

class BestEffortJsonSchemaEncoder(validationMode: ValidationMode) {

  private val classLoader = this.getClass.getClassLoader
  private val jsonEncoder = BestEffortJsonEncoder(failOnUnkown = false, this.getClass.getClassLoader)

  private val optionalEncoders = ServiceLoader.load(classOf[ToJsonBasedOnSchemaEncoder], classLoader).asScala.map(_.encoder(this.encodeBasedOnSchema))
  private val highPriority: PartialFunction[EncodeInput, EncodeOutput] = Map()


  final def encodeOrError(value: Any, schema: Schema): Json = {
    val json = encode(value, schema).valueOr(errors => throw new RuntimeException(errors.toList.mkString(",")))
    json
  }

  private def encodeObject(fields: Map[String, _], parentSchema: ObjectSchema): EncodeOutput = {
    fields
      .map(field => (field, parentSchema.getPropertySchemas.get(field._1)))
      .collect {
        case ((fieldName, value), propertySchema) if (propertySchema != null) && notNullOrRequired(fieldName, value, parentSchema) => encode(value, propertySchema).map(fieldName -> _)
        case ((fieldName, _), null) if !parentSchema.permitsAdditionalProperties() => error(s"Not expected field with name: ${fieldName} for schema: $parentSchema and policy $validationMode does not allow redundant")
      }
      .toList.sequence.map { values => Json.fromFields(values) }
  }

  private def notNullOrRequired(fieldName: String, value: Any, parentSchema: ObjectSchema): Boolean = {
    value != null || parentSchema.getRequiredProperties.contains(fieldName)
  }

  private def encodeCollection(collection: Traversable[_], schema: ArraySchema): EncodeOutput = {
    collection.map(el => encode(el, schema.getAllItemSchema)).toList.sequence.map(l => Json.fromValues(l))
  }

  def encodeBasedOnSchema(input: EncodeInput): EncodeOutput = {
    val (value, schema, fieldName) = input
    (schema, value) match {
      case (schema: ObjectSchema, map: scala.collection.Map[String@unchecked, _]) => encodeObject(map.toMap, schema)
      case (schema: ObjectSchema, map: java.util.Map[String@unchecked, _]) => encodeObject(map.toMap, schema)
      case (schema: ArraySchema, value: Traversable[_]) => encodeCollection(value, schema)
      case (schema: ArraySchema, value: java.util.Collection[_]) => encodeCollection(value.toArray, schema)
      case (schema: StringSchema, value: Any) => encodeStringSchema(schema, value, fieldName)
      case (_: NumberSchema, value: Long) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Double) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Float) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Int) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: java.math.BigDecimal) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: java.math.BigInteger) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Number) => Valid(jsonEncoder.encode(value.doubleValue()))
      case (_: NullSchema, null) => Valid(Json.Null)
      case (_: NullSchema, None) => Valid(Json.Null)
      case (_: BooleanSchema, value: Boolean) => Valid(Json.fromBoolean(value))
      case (_: EnumSchema, value: Enum[_]) => Valid(Json.fromString(value.toString))
      case (null, value: Any) if validationMode == ValidationMode.lax => Valid(jsonEncoder.encode(value))
      case (_, null) if validationMode != ValidationMode.lax => error(s"Not expected null for field: $fieldName with schema: $schema")
      case (null, _) if validationMode != ValidationMode.lax => error(s"Not expected null for field: $fieldName with schema: $schema")
      case (cs: CombinedSchema, value) => cs.getSubschemas.asScala.view.map(encodeBasedOnSchema(value, _, fieldName)).find(_.isValid)
        .getOrElse(error(s"Not expected type: ${value.getClass.getName} for field: $fieldName with schema: $cs"))
      case (_, _) => error(s"Not expected type: ${value.getClass.getName} for field: $fieldName with schema: $schema")
    }
  }

  private def encodeStringSchema(schema: StringSchema, value: Any, fieldName: Option[String] = None) = {
    (schema.getFormatValidator.formatName(), value) match {
      case ("date-time", zdt: ZonedDateTime) => Valid(jsonEncoder.encode(zdt))
      case ("date-time", odt: OffsetDateTime) => Valid(jsonEncoder.encode(odt))
      case ("date-time", _: Any) => error(s"Not expected type: ${value.getClass.getName} for field: $fieldName with schema: $schema")
      case ("date", ldt: LocalDate) => Valid(jsonEncoder.encode(ldt))
      case ("date", _: Any) => error(s"Not expected type: ${value.getClass.getName} for field: $fieldName with schema: $schema")
      case ("time", ot: OffsetTime) => Valid(jsonEncoder.encode(ot))
      case ("time", _: Any) => error(s"Not expected type: ${value.getClass.getName} for field: $fieldName with schema: $schema")
      case ("unnamed-format", _: Any) => Valid(jsonEncoder.encode(value))
      case _ => error(s"Not expected type: ${value.getClass.getName} for field: $fieldName with schema: $schema")
    }
  }

  private def error(str: String): Invalid[NonEmptyList[String]] = Invalid(NonEmptyList.of(str))

  def encode(value: Any, schema: Schema, fieldName: Option[String] = None): EncodeOutput = {
    optionalEncoders.foldLeft(highPriority)(_.orElse(_)).applyOrElse[EncodeInput, EncodeOutput]((value, schema, fieldName), encodeBasedOnSchema)
  }

}
