package pl.touk.nussknacker.engine.json.encode

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import cats.implicits.toTraverseOps
import io.circe.Json
import org.everit.json.schema._
import pl.touk.nussknacker.engine.util.json._

import java.time.{LocalDate, OffsetDateTime, OffsetTime, ZonedDateTime}
import java.util.ServiceLoader
import scala.collection.convert.ImplicitConversions.`map AsScala`
import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter


object BestEffortJsonSchemaEncoder {

  import pl.touk.nussknacker.engine.util.json.JsonSchemaImplicits._

  private val classLoader = this.getClass.getClassLoader
  private val jsonEncoder = BestEffortJsonEncoder(failOnUnkown = false, this.getClass.getClassLoader)

  private val optionalEncoders = ServiceLoader.load(classOf[ToJsonBasedOnSchemaEncoder], classLoader).asScala.map(_.encoder(this.encodeBasedOnSchema))
  private val highPriority: PartialFunction[EncodeInput, EncodeOutput] = Map()

  final def encodeOrError(value: Any, schema: Schema): Json = {
    encodeWithJsonValidation(value, schema).valueOr(errors => throw new RuntimeException(errors.toList.mkString(",")))
  }

  private def encodeObject(fields: Map[String, _], parentSchema: ObjectSchema): EncodeOutput = {
    fields.keys.toList.union(parentSchema.getPropertySchemas.keySet.asScala.toList).distinct.map{ key =>
      val schema = Option(parentSchema.getPropertySchemas.get(key))
      val value = fields.getOrElse(key, null)
      (key, value, fields.contains(key), schema)
    }.collect {
      case (fieldName, null, true, Some(schema)) if schema.isNullableSchema => Valid((fieldName, Json.Null))
      case (fieldName, null, false, Some(_)) if parentSchema.getRequiredProperties.contains(fieldName) =>
        error(s"Missing property: $fieldName for schema: $parentSchema.")
      case (fieldName, value, true, Some(schema)) =>
        encode(value, schema, Some(fieldName)).map(fieldName -> _)
      case (fieldName, _, _, None) if !parentSchema.permitsAdditionalProperties() =>
        error(s"Not expected field with name: $fieldName for schema: $parentSchema.")
      case (fieldName, value, _, None) => Option(parentSchema.getSchemaOfAdditionalProperties) match {
        case Some(additionalPropertySchema) => encode(value, additionalPropertySchema).map(fieldName -> _)
        case None => Valid(jsonEncoder.encode(value)).map(fieldName -> _)
      }
    }.sequence.map { values => Json.fromFields(values) }
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
      case (cs: CombinedSchema, value) => cs.getSubschemas.asScala.view.map(encodeBasedOnSchema(value, _, fieldName)).find(_.isValid)
        .getOrElse(error(value.getClass.getName, cs.toString, fieldName))
      case (schema: StringSchema, value: Any) => encodeStringSchema(schema, value, fieldName)
      case (_: NumberSchema, value: Long) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Double) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Float) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Int) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: java.math.BigDecimal) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: java.math.BigInteger) => Valid(jsonEncoder.encode(value))
      case (_: NumberSchema, value: Number) => Valid(jsonEncoder.encode(value.doubleValue()))
      case (_: BooleanSchema, value: Boolean) => Valid(Json.fromBoolean(value))
      case (_: EnumSchema, value: Enum[_]) => Valid(Json.fromString(value.toString))
      case (_: NullSchema, null) => Valid(Json.Null)
      case (_: NullSchema, None) => Valid(Json.Null)
      case (_, null) => error("null", schema.toString, fieldName)
      case (_, _) =>
        error(value.getClass.getName, schema.toString, fieldName)
    }
  }

  private def encodeStringSchema(schema: StringSchema, value: Any, fieldName: Option[String] = None): Validated[NonEmptyList[String], Json] = {
    (schema.getFormatValidator.formatName(), value) match {
      case ("date-time", zdt: ZonedDateTime) => Valid(jsonEncoder.encode(zdt))
      case ("date-time", odt: OffsetDateTime) => Valid(jsonEncoder.encode(odt))
      case ("date-time", _: Any) => error(value.getClass.getName, schema.toString, fieldName)
      case ("date", ldt: LocalDate) => Valid(jsonEncoder.encode(ldt))
      case ("date", _: Any) => error(value.getClass.getName, schema.toString, fieldName)
      case ("time", ot: OffsetTime) => Valid(jsonEncoder.encode(ot))
      case ("time", _: Any) => error(value.getClass.getName, schema.toString, fieldName)
      case ("unnamed-format", _: String) => Valid(jsonEncoder.encode(value))
      case _ => error(value.getClass.getName, schema.toString, fieldName)
    }
  }

  private def error(str: String): Invalid[NonEmptyList[String]] = Invalid(NonEmptyList.of(str))

  private def error(runtimeType: String, schema: String, fieldName: Option[String]): Invalid[NonEmptyList[String]] =
    Invalid(NonEmptyList.of(s"Not expected type: $runtimeType for field${fieldName.map(f => s": '$f'").getOrElse("")} with schema: $schema."))

  private[encode] def encodeWithJsonValidation(value: Any, schema: Schema, fieldName: Option[String] = None): EncodeOutput =
    encode(value, schema, fieldName).andThen { result =>
      val json = JsonSchemaUtils.circeToJson(result)
      schema
        .validateData(json)
        .leftMap(errorMsg => NonEmptyList.of(errorMsg))
        .map(_ => result) //we return base json (e.g. without felt defaults - there is no need to put these data as output)
    }

  private[encode] def encode(value: Any, schema: Schema, fieldName: Option[String] = None): EncodeOutput = {
    optionalEncoders.foldLeft(highPriority)(_.orElse(_)).applyOrElse[EncodeInput, EncodeOutput]((value, schema, fieldName), encodeBasedOnSchema)
  }

}
