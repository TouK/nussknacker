package pl.touk.nussknacker.engine.avro.encode

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits._
import org.apache.avro.generic.GenericData.EnumSymbol
import org.apache.avro.generic.{GenericContainer, GenericData}
import org.apache.avro.util.Utf8
import org.apache.avro.{AvroRuntimeException, LogicalTypes, Schema}
import pl.touk.nussknacker.engine.avro.LogicalTypesGenericRecordBuilder
import pl.touk.nussknacker.engine.avro.schema.AvroStringSettings.forceUsingStringForStringSchema
import pl.touk.nussknacker.engine.avro.schema.{AvroSchemaEvolution, DefaultAvroSchemaEvolution}

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.chrono.ChronoZonedDateTime
import java.time.{Instant, LocalDate, LocalTime, OffsetDateTime}
import java.util
import java.util.UUID
import scala.math.BigDecimal.RoundingMode

class BestEffortAvroEncoder(avroSchemaEvolution: AvroSchemaEvolution, validationMode: ValidationMode) {

  import scala.collection.JavaConverters._

  type WithError[T] = ValidatedNel[String, T]

  def encodeOrError(value: Any, schema: Schema): AnyRef = {
    encode(value, schema).valueOr(l => throw new AvroRuntimeException(l.toList.mkString(",")))
  }

  // It is quite similar logic to GenericDatumReader.readWithConversion but instead of reading from decoder, it read directly from value
  def encode(value: Any, schema: Schema, fieldName: Option[String] = None): WithError[AnyRef] = {
    (schema.getType, value) match {
      case (_, Some(nested)) =>
        encode(nested, schema)
      case (Schema.Type.RECORD, container: GenericContainer) =>
        encodeGenericContainer(container, schema)
      case (Schema.Type.RECORD, map: collection.Map[String@unchecked, _]) =>
        encodeRecord(map, schema)
      case (Schema.Type.RECORD, map: util.Map[String@unchecked, _]) =>
        encodeRecord(map, schema)
      case (Schema.Type.ENUM, symbol: CharSequence) =>
        encodeEnumOrError(symbol.toString, schema, fieldName)
      case (Schema.Type.ENUM, symbol: EnumSymbol) =>
        encodeEnumOrError(symbol.toString, schema, fieldName)
      case (Schema.Type.ARRAY, collection: Traversable[_]) =>
        encodeCollection(collection, schema)
      case (Schema.Type.ARRAY, collection: util.Collection[_]) =>
        encodeCollection(collection.asScala, schema)
      case (Schema.Type.MAP, map: collection.Map[_, _]) =>
        encodeMap(map, schema)
      case (Schema.Type.MAP, map: util.Map[_, _]) =>
        encodeMap(map.asScala, schema)
      case (Schema.Type.UNION, _) =>
        schema.getTypes.asScala.toStream.flatMap { subTypeSchema =>
          encode(value, subTypeSchema).toOption
        }.headOption.map(Valid(_)).getOrElse {
          error(s"Cant't find matching union subtype for value: $value for field: $fieldName with schema: $schema")
        }
      case (Schema.Type.FIXED, str: CharSequence) =>
        val bytes = str.toString.getBytes(StandardCharsets.UTF_8)
        encodeFixed(bytes, schema)
      case (Schema.Type.FIXED, buffer: ByteBuffer) =>
        encodeFixed(buffer.array(), schema)
      case (Schema.Type.FIXED, bytes: Array[Byte]) =>
        encodeFixed(bytes, schema)
      case (Schema.Type.STRING, str: String) =>
        Valid(encodeString(str))
      case (Schema.Type.STRING, str: CharSequence) =>
        Valid(str)
      case (Schema.Type.STRING, uuid: UUID) if schema.getLogicalType == LogicalTypes.uuid() =>
        Valid(uuid)
      case (Schema.Type.BYTES, str: CharSequence) =>
        Valid(ByteBuffer.wrap(str.toString.getBytes(StandardCharsets.UTF_8)))
      case (Schema.Type.BYTES, bytes: Array[Byte]) =>
        Valid(ByteBuffer.wrap(bytes))
      case (Schema.Type.BYTES, buffer: ByteBuffer) =>
        Valid(buffer)
      case (Schema.Type.FIXED | Schema.Type.BYTES, decimal: java.math.BigDecimal) if schema.getLogicalType != null && schema.getLogicalType.isInstanceOf[LogicalTypes.Decimal] =>
        Valid(alignDecimalScale(decimal, schema))
      case (Schema.Type.FIXED | Schema.Type.BYTES, number: Number) if schema.getLogicalType != null && schema.getLogicalType.isInstanceOf[LogicalTypes.Decimal] =>
        Valid(alignDecimalScale(new java.math.BigDecimal(number.toString), schema))
      case (Schema.Type.INT, number: Number) =>
        Valid(number.intValue().underlying())
      case (Schema.Type.INT, time: LocalTime) if schema.getLogicalType == LogicalTypes.timeMillis() =>
        Valid(time)
      case (Schema.Type.INT, time: LocalDate) if schema.getLogicalType == LogicalTypes.date() =>
        Valid(time)
      case (Schema.Type.LONG, number: Number) =>
        Valid(number.longValue().underlying())
      case (Schema.Type.LONG, instant: Instant) if schema.getLogicalType == LogicalTypes.timestampMillis() || schema.getLogicalType == LogicalTypes.timestampMicros() =>
        Valid(instant)
      case (Schema.Type.LONG, zoned: ChronoZonedDateTime[_]) if schema.getLogicalType == LogicalTypes.timestampMillis() || schema.getLogicalType == LogicalTypes.timestampMicros() =>
        Valid(zoned.toInstant)
      case (Schema.Type.LONG, offset: OffsetDateTime) if schema.getLogicalType == LogicalTypes.timestampMillis() || schema.getLogicalType == LogicalTypes.timestampMicros() =>
        Valid(offset.toInstant)
      case (Schema.Type.LONG, time: LocalTime) if schema.getLogicalType == LogicalTypes.timeMicros() =>
        Valid(time)
      case (Schema.Type.FLOAT, number: Number) =>
        Valid(number.floatValue().underlying())
      case (Schema.Type.DOUBLE, number: Number) =>
        Valid(number.doubleValue().underlying())
      case (Schema.Type.BOOLEAN, boolean: java.lang.Boolean) =>
        Valid(boolean)
      case (Schema.Type.NULL, null) =>
        Valid(null)
      case (Schema.Type.NULL, None) =>
        Valid(null)
      case (_, null) =>
        error(s"Not expected null for field: $fieldName with schema: $schema")
      case (_, _) =>
        error(s"Not expected type: ${value.getClass.getName} for field: $fieldName with schema: $schema")
    }
  }

  private def alignDecimalScale(decimal: java.math.BigDecimal, schema: Schema): java.math.BigDecimal = {
    val decimalLogicalType = schema.getLogicalType.asInstanceOf[LogicalTypes.Decimal]
    decimal.setScale(decimalLogicalType.getScale, RoundingMode.DOWN).bigDecimal
  }

  def encodeEnumOrError(symbol: String, schema: Schema, fieldName: Option[String]): WithError[EnumSymbol] =
    if (!schema.hasEnumSymbol(symbol))
      error(s"Not expected symbol: $symbol for field: $fieldName with schema: $schema")
    else
      Valid(new EnumSymbol(schema, symbol))

  def encodeRecordOrError(fields: collection.Map[String, _], schema: Schema): GenericData.Record = {
    encodeRecordOrError(fields.asJava, schema)
  }

  def encodeRecordOrError(fields: java.util.Map[String, _], schema: Schema): GenericData.Record = {
    encodeRecord(fields, schema).valueOr(l => throw new AvroRuntimeException(l.toList.mkString(",")))
  }

  def encodeRecord(fields: collection.Map[String, _], schema: Schema): WithError[GenericData.Record] = {
    encodeRecord(fields.asJava, schema)
  }

  def encodeRecord(fields: util.Map[String, _], schema: Schema): WithError[GenericData.Record] = {
    fields.asScala.map(kv => (kv, schema.getField(kv._1))).collect {
      case ((fieldName, value), field) if field != null =>
        val fieldSchema = field.schema()
        encode(value, fieldSchema, Some(fieldName)).map(fieldName -> _)
      case ((fieldName, _), null) if !validationMode.acceptRedundant =>
        error(s"Not expected field with name: $fieldName for schema: $schema and policy $validationMode does not allow redundant")
    }.toList.sequence.map { values =>
      val builder = new LogicalTypesGenericRecordBuilder(schema)
      values.foreach {
        case (k, v) => builder.set(k, v)
      }
      builder.build()
    }
    //TODO: Check optional?
  }

  private def encodeGenericContainer(container: GenericContainer, schema: Schema): WithError[GenericContainer] = {
    if (!avroSchemaEvolution.canBeEvolved(container, schema)) {
      error(s"Not expected container: ${container.getSchema} for schema: $schema")
    } else {
      Valid(container)
    }
  }

  private def encodeMap(map: collection.Map[_, _], schema: Schema): WithError[util.Map[CharSequence, AnyRef]] = {
    map.map {
      case (k: String, v) =>
        encode(v, schema.getValueType, Some(k)).map(encodeString(k) -> _)
      case (k: CharSequence, v) =>
        encode(v, schema.getValueType, Some(k.toString)).map(k -> _)
      case (k, v) =>
        error(s"Not expected type: ${k.getClass.getName} as a key of map for schema: $schema")
    }.toList.sequence.map(m => new util.HashMap(m.toMap.asJava))
  }

  private def encodeCollection(collection: Traversable[_], schema: Schema): WithError[java.util.List[AnyRef]] = {
    collection.map(el => encode(el, schema.getElementType)).toList.sequence.map(l => new util.ArrayList(l.asJava))
  }

  private def encodeFixed(bytes: Array[Byte], schema: Schema): WithError[GenericData.Fixed] = {
    if (bytes.length != schema.getFixedSize) {
      error(s"Fixed size not matches: ${bytes.length} != ${schema.getFixedSize} for schema: $schema")
    } else {
      val fixed = new GenericData.Fixed(schema)
      fixed.bytes(bytes)
      Valid(fixed)
    }
  }

  private def error(str: String): Invalid[NonEmptyList[String]] = Invalid(NonEmptyList.of(str))

  private def encodeString(str: String): CharSequence = {
    if (forceUsingStringForStringSchema) str else new Utf8(str)
  }

}

object BestEffortAvroEncoder {

  final private val DefaultSchemaEvolution = new DefaultAvroSchemaEvolution

  def apply(validationMode: ValidationMode): BestEffortAvroEncoder = new BestEffortAvroEncoder(DefaultSchemaEvolution, validationMode)
}
