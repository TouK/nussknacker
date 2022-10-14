package pl.touk.nussknacker.engine.json.encode

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.toTraverseOps
import io.circe.Json
import org.everit.json.schema.{ArraySchema, BooleanSchema, EnumSchema, NullSchema, NumberSchema, ObjectSchema, Schema, StringSchema}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.util.json.{BestEffortJsonEncoder, ToJsonBasedOnSchemaEncoder, ToJsonEncoder}

import java.time.{LocalDate, LocalDateTime, LocalTime, OffsetTime, ZonedDateTime}
import java.util.ServiceLoader
import scala.collection.convert.ImplicitConversions.`map AsScala`
import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter

class BestEffortJsonSchemaEncoder(validationMode: ValidationMode) {
  type WithError[T] = ValidatedNel[String, T]

  private val classLoader = this.getClass.getClassLoader
  private val jsonEncoder = BestEffortJsonEncoder(failOnUnkown = false, this.getClass.getClassLoader)

  private val optionalEncoders = ServiceLoader.load(classOf[ToJsonBasedOnSchemaEncoder], classLoader).asScala.map(_.encoder(this.encode))
  private val highPriority: PartialFunction[(Any, Schema, Option[String]), WithError[Json]] = Map()


  final def encodeOrError(value: Any, schema: Schema): Json = {
    encode(value, schema).valueOr(errors => throw new RuntimeException(errors.toList.mkString(",")))
  }

  private def encodeObject(fields: Map[String, _], parentSchema: ObjectSchema): WithError[Json] = {
    fields
      .map(field => (field, parentSchema.getPropertySchemas.get(field._1)))
      .collect {
        case (field, propertySchema) if (propertySchema != null) && notNullOrRequired(field, parentSchema) => encode(field._2, propertySchema).map(field._1 -> _)
        case (filed, null) if validationMode != ValidationMode.lax => error(s"Not expected field with name: ${filed._1} for schema: $parentSchema and policy $validationMode does not allow redundant")
      }
      .toList.sequence.map { values => Json.fromFields(values) }
  }

  private def notNullOrRequired(field: (String, _), parentSchema: ObjectSchema): Boolean = {
    field._2 != null || parentSchema.getRequiredProperties.contains(field._1)
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
      case (_, _) if validationMode != ValidationMode.lax => error(s"Not expected type: ${value.getClass.getName} for field: $fieldName with schema: $schema")
    }
  }

  private def encodeStringSchema(schema: StringSchema, value: Any, fieldName: Option[String] = None) = {
    (schema.getFormatValidator.formatName(), value) match {
      case ("date-time", zdt: ZonedDateTime) => Valid(jsonEncoder.encode(zdt))
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

  def encode(value: Any, schema: Schema, fieldName: Option[String] = None): WithError[Json] = {
    optionalEncoders.foldLeft(highPriority)(_.orElse(_)).applyOrElse((value, schema, fieldName), (vs: (Any, Schema, Option[String])) =>
      encodeBasedOnSchema(vs._1, vs._2, fieldName)
    )
  }

}
