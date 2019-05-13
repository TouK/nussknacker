package pl.touk.nussknacker.engine.avro.encode

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits._
import org.apache.avro.generic.GenericData.EnumSymbol
import org.apache.avro.generic.{GenericData, GenericRecordBuilder}
import org.apache.avro.util.Utf8
import org.apache.avro.{AvroRuntimeException, Schema}
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax

import scala.util.control.Exception.catching

object BestEffortAvroEncoder {

  import scala.collection.JavaConverters._

  type WithError[T] = ValidatedNel[String, T]

  // It is quite similar logic to GenericDatumReader.readWithConversion but instead of reading from decoder, it read directly from value
  def encode(value: Any, schema: Schema): WithError[Any] = {
    (schema.getType, value) match {
      case (_, Some(nested)) =>
        encode(nested, schema)
      case (Schema.Type.RECORD, map: collection.Map[String@unchecked, _]) =>
        encodeRecord(map, schema)
      case (Schema.Type.RECORD, map: util.Map[String@unchecked, _]) =>
        encodeRecord(map, schema)
      case (Schema.Type.ENUM, str: String) =>
        if (!schema.hasEnumSymbol(str)) {
          error(s"Not expected symbol: $value for schema: $schema")
        } else {
          Valid(new EnumSymbol(schema, str))
        }
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
          error(s"Cant't find matching union subtype for value: $value for schema: $schema")
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
      case (Schema.Type.BYTES, str: CharSequence) =>
        Valid(ByteBuffer.wrap(str.toString.getBytes(StandardCharsets.UTF_8)))
      case (Schema.Type.BYTES, bytes: Array[Byte]) =>
        Valid(ByteBuffer.wrap(bytes))
      case (Schema.Type.BYTES, buffer: ByteBuffer) =>
        Valid(buffer)
      case (Schema.Type.INT, number: Number) =>
        Valid(number.intValue())
      case (Schema.Type.LONG, number: Number) =>
        Valid(number.longValue())
      case (Schema.Type.FLOAT, number: Number) =>
        Valid(number.floatValue())
      case (Schema.Type.DOUBLE, number: Number) =>
        Valid(number.doubleValue())
      case (Schema.Type.BOOLEAN, boolean: Boolean) =>
        Valid(boolean)
      case (Schema.Type.NULL, null) =>
        Valid(null)
      case (Schema.Type.NULL, None) =>
        Valid(null)
      case (_, null) =>
        error(s"Not expected null for schema: $schema")
      case (_, _) =>
        error(s"Not expected type: ${value.getClass.getName} for schema: $schema")
    }
  }

  def encodeRecordOrError(fields: collection.Map[String, _], schema: Schema): GenericData.Record = {
    encodeRecordOrError(fields.asJava, schema)
  }

  def encodeRecordOrError(fields: java.util.Map[String, _], schema: Schema): GenericData.Record = {
    encodeRecord(fields, schema).fold(l => throw new AvroRuntimeException(l.toList.mkString(",")), identity[GenericData.Record])
  }

  def encodeRecord(fields: collection.Map[String, _], schema: Schema): WithError[GenericData.Record] = {
    encodeRecord(fields.asJava, schema)
  }

  def encodeRecord(fields: util.Map[String, _], schema: Schema): WithError[GenericData.Record] = {
    fields.asScala.map {
      case (fieldName, value) =>
        val field = schema.getField(fieldName)
        if (field == null) {
          error(s"Not expected field with name: $fieldName for schema: $schema")
        } else {
          val fieldSchema = field.schema()
          encode(value, fieldSchema).map(fieldName -> _)
        }
    }.toList.sequence.map { values =>
      var builder = new GenericRecordBuilder(schema)
      values.foreach {
        case (k, v) => builder.set(k, v)
      }
      builder.build()
    }
  }

  private def encodeMap(map: collection.Map[_, _], schema: Schema): WithError[util.Map[CharSequence, Any]] = {
    map.map {
      case (k: String, v) =>
        encode(v, schema.getValueType).map(encodeString(k) -> _)
      case (k: CharSequence, v) =>
        encode(v, schema.getValueType).map(k -> _)
      case (k, v) =>
        error(s"Not expected type: ${k.getClass.getName} as a key of map for schema: $schema")
    }.toList.sequence.map(_.toMap.asJava)
  }

  private def encodeCollection(collection: Traversable[_], schema: Schema): WithError[Any] = {
    collection.map(el => encode(el, schema.getElementType)).toList.sequence.map(_.toBuffer.asJava)
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

  private def error(str: String) = Invalid(NonEmptyList.of(str))

  private def encodeString(str: String): Utf8 = {
    new Utf8(str)
  }

}
