package pl.touk.nussknacker.engine.json.encode

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.toTraverseOps
import io.circe.Json
import org.everit.json.schema.{ArraySchema, BooleanSchema, EnumSchema, NullSchema, NumberSchema, ObjectSchema, Schema, StringSchema}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import scala.collection.convert.ImplicitConversions.`map AsScala`

class BestEffortJsonSchemaEncoder(validationMode: ValidationMode) {

  private val jsonEncoder = BestEffortJsonEncoder(failOnUnkown = false, this.getClass.getClassLoader)


  type WithError[T] = ValidatedNel[String, T]

  final def encodeOrError(value: Any, schema: Schema): Json = {
    encode(value, schema).valueOr(errors => throw new RuntimeException(errors.toList.mkString(",")))
  }

  def encodeObject(fields: Map[String, _], objectSchema: ObjectSchema): WithError[Json] = {
    fields
      .map(kv => (kv, objectSchema.getPropertySchemas.get(kv._1)))
      .collect {
        case (tuple, propertySchema) if propertySchema != null => encode(tuple._2, propertySchema).map(tuple._1 -> _)
        case (tuple, null) if validationMode != ValidationMode.lax => error(s"Not expected field with name: ${tuple._1} for schema: $objectSchema and policy $validationMode does not allow redundant")
      }
      .toList.sequence.map { values => Json.fromFields(values) }
  }


  private def encodeCollection(collection: Traversable[_], schema: ArraySchema): WithError[Json] = {
    collection.map(el => encode(el, schema.getAllItemSchema)).toList.sequence.map(l => Json.fromValues(l))
  }

  private def encodeBasedOnSchema(value: Any, schema: Schema, fieldName: Option[String] = None): WithError[Json] = {
    (schema, value) match {
      case (schema: ObjectSchema, map: scala.collection.Map[String@unchecked, _]) => encodeObject(map.toMap, schema)
      case (schema: ObjectSchema, map: java.util.Map[String@unchecked, _]) => encodeObject(map.toMap, schema)
      case (schema: ArraySchema, value: Traversable[_]) => encodeCollection(value, schema)
      case (schema: ArraySchema, value: java.util.Collection[_]) => encodeCollection(value.toArray, schema)
      case (_: StringSchema, value: String) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Long) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Double) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Float) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Int) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: java.math.BigDecimal) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: java.math.BigInteger) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Number) => Valid(jsonEncoder.encode(value.doubleValue()))
      // todo dates
      //      case a: LocalDate => Encoder[LocalDate].apply(a)
      //      case a: LocalTime => Encoder[LocalTime].apply(a)
      //      case a: LocalDateTime => Encoder[LocalDateTime].apply(a)
      //      Default implementation serializes to ISO_ZONED_DATE_TIME which is not handled well by some parsers...
      //      case a: ZonedDateTime => encodeZonedDateTimeWithFormatter(DateTimeFormatter.ISO_OFFSET_DATE_TIME).apply(a)
      //      case a: Instant => Encoder[Instant].apply(a)
      //      case a: OffsetDateTime => Encoder[OffsetDateTime].apply(a)
      //      case a: UUID => safeString(a.toString)
      //      case a: DisplayJson => a.asJson
      case (_: NullSchema, null) => Valid(Json.Null)
      case (_: NullSchema, None) => Valid(Json.Null)
      case (_: BooleanSchema, value: Boolean) => Valid(Json.fromBoolean(value))
      case (_: EnumSchema, value: Enum[_]) => Valid(Json.fromString(value.toString))
      case (_, null) if validationMode != ValidationMode.lax => error(s"Not expected null for field: $fieldName with schema: $schema")
      case (null, value: Any) if validationMode == ValidationMode.lax => Valid(Json.fromString(value.toString))
      case (null, _) if validationMode != ValidationMode.lax => error(s"Not expected null for field: $fieldName with schema: $schema")
      case (_, _) if validationMode != ValidationMode.lax => error(s"Not expected type: ${value.getClass.getName} for field: $fieldName with schema: $schema")
    }
  }

  private def error(str: String): Invalid[NonEmptyList[String]] = Invalid(NonEmptyList.of(str))

  def encode(value: Any, schema: Schema, fieldName: Option[String] = None): WithError[Json] = {
    encodeBasedOnSchema(value, schema, fieldName)
  }
}
