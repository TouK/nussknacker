package pl.touk.nussknacker.engine.avro.encode

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util

import org.apache.avro.generic.GenericData.EnumSymbol
import org.apache.avro.generic.{GenericData, GenericRecordBuilder}
import org.apache.avro.util.Utf8
import org.apache.avro.{AvroRuntimeException, Schema}

import scala.util.control.Exception.catching

object BestEffortAvroEncoder {

  import scala.collection.JavaConverters._

  // It is quite similar logic to GenericDatumReader.readWithConversion but instead of reading from decoder, it read directly from value
  def encode(value: Any, schema: Schema): Any = {
    (schema.getType, value) match {
      case (_, Some(nested)) =>
        encode(nested, schema)
      case (Schema.Type.RECORD, map: collection.Map[String@unchecked, _]) =>
        encodeRecord(map, schema)
      case (Schema.Type.RECORD, map: util.Map[String@unchecked, _]) =>
        encodeRecord(map, schema)
      case (Schema.Type.ENUM, str: String) =>
        if (!schema.hasEnumSymbol(str))
          throw new AvroRuntimeException(s"Not expected symbol: $value for schema: $schema")
        new EnumSymbol(schema, str)
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
          catching(classOf[AvroRuntimeException]) opt encode(value, subTypeSchema)
        }.headOption.getOrElse {
          throw new AvroRuntimeException(s"Cant't find matching union subtype for value: $value for schema: $schema")
        }
      case (Schema.Type.FIXED, str: CharSequence) =>
        val bytes = str.toString.getBytes(StandardCharsets.UTF_8)
        encodeFixed(bytes, schema)
      case (Schema.Type.FIXED, buffer: ByteBuffer) =>
        encodeFixed(buffer.array(), schema)
      case (Schema.Type.FIXED, bytes: Array[Byte]) =>
        encodeFixed(bytes, schema)
      case (Schema.Type.STRING, str: String) =>
        encodeString(str)
      case (Schema.Type.STRING, str: CharSequence) =>
        str
      case (Schema.Type.BYTES, str: CharSequence) =>
        ByteBuffer.wrap(str.toString.getBytes(StandardCharsets.UTF_8))
      case (Schema.Type.BYTES, bytes: Array[Byte]) =>
        ByteBuffer.wrap(bytes)
      case (Schema.Type.BYTES, buffer: ByteBuffer) =>
        buffer
      case (Schema.Type.INT, number: Number) =>
        number.intValue()
      case (Schema.Type.LONG, number: Number) =>
        number.longValue()
      case (Schema.Type.FLOAT, number: Number) =>
        number.floatValue()
      case (Schema.Type.DOUBLE, number: Number) =>
        number.doubleValue()
      case (Schema.Type.BOOLEAN, boolean: Boolean) =>
        boolean
      case (Schema.Type.NULL, null) =>
        null
      case (Schema.Type.NULL, None) =>
        null
      case (_, null) =>
        throw new AvroRuntimeException(s"Not expected null for schema: $schema")
      case (_, _) =>
        throw new AvroRuntimeException(s"Not expected type: ${value.getClass.getName} for schema: $schema")
    }
  }

  def encodeRecord(fields: collection.Map[String, _], schema: Schema): GenericData.Record = {
    encodeRecord(fields.asJava, schema)
  }

  def encodeRecord(fields: util.Map[String, _], schema: Schema): GenericData.Record = {
    var builder = new GenericRecordBuilder(schema)
    fields.asScala.foreach {
      case (fieldName, value) =>
        val field = schema.getField(fieldName)
        if (field == null) {
          throw new AvroRuntimeException(s"Not expected field with name: $fieldName for schema: $schema")
        } else {
          val fieldSchema = field.schema()
          builder.set(fieldName, encode(value, fieldSchema))
        }
    }
    builder.build()
  }

  private def encodeMap(map: collection.Map[_, _], schema: Schema) = {
    map.map {
      case (k: String, v) =>
        encodeString(k) -> encode(v, schema.getValueType)
      case (k: CharSequence, v) =>
        k -> encode(v, schema.getValueType)
      case (k, v) =>
        throw new AvroRuntimeException(s"Not expected type: ${k.getClass.getName} as a key of map for schema: $schema")
    }.asJava
  }

  private def encodeCollection(collection: Traversable[_], schema: Schema) = {
    collection.map(el => encode(el, schema.getElementType)).toBuffer.asJava
  }

  private def encodeFixed(bytes: Array[Byte], schema: Schema) = {
    if (bytes.length != schema.getFixedSize)
      throw new AvroRuntimeException(s"Fixed size not matches: ${bytes.length} != ${schema.getFixedSize} for schema: $schema")
    val fixed = new GenericData.Fixed(schema)
    fixed.bytes(bytes)
    fixed
  }

  private def encodeString(str: String) = {
    new Utf8(str)
  }

}
