package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload

import io.circe.Decoder
import io.circe.Json.fromString
import org.apache.avro.Conversions.UUIDConversion
import org.apache.avro.data.TimeConversions
import org.apache.avro.generic.GenericRecord
import org.apache.avro.specific.SpecificRecordBase
import org.apache.avro.{LogicalTypes, Schema}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload.JsonPayloadToAvroConverter._
import tech.allegro.schema.json2avro.converter.types.AvroTypeConverter
import tech.allegro.schema.json2avro.converter.{CompositeJsonToAvroReader, JsonAvroConverter}

import java.math.RoundingMode
import java.time.{Instant, LocalDate, LocalTime}
import java.util

class JsonPayloadToAvroConverter(specificClass: Option[Class[SpecificRecordBase]]) {

  private val converter = new JsonAvroConverter(new CompositeJsonToAvroReader(
    DateConverter, TimeMillisConverter, TimeMicrosConverter, TimestampMillisConverter, TimestampMicrosConverter,
    UUIDConverter, DecimalConverter
  ))

  def convert(payload: Array[Byte], schema: Schema): GenericRecord = {
    specificClass match {
      case Some(kl) => converter.convertToSpecificRecord(payload, kl, schema)
      case None => converter.convertToGenericDataRecord(payload, schema)
    }
  }

}

object JsonPayloadToAvroConverter {

  object DateConverter extends AvroTypeConverter {
    private val conversion = new TimeConversions.DateConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.INT && schema.getLogicalType == LogicalTypes.date()

    override def convert(field: Schema.Field, schema: Schema, jsonValue: AnyRef, path: util.Deque[String], silently: Boolean): AnyRef = {
      jsonValue match {
        case number: Number => conversion.fromInt(number.intValue(), schema, schema.getLogicalType)
        case str: String => Decoder[LocalDate].decodeJson(fromString(str)).fold(throw _, identity)
        case other => other
      }
    }
  }

  object TimeMillisConverter extends AvroTypeConverter {
    private val conversion = new TimeConversions.TimeMillisConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.INT && schema.getLogicalType == LogicalTypes.timeMillis()

    override def convert(field: Schema.Field, schema: Schema, jsonValue: AnyRef, path: util.Deque[String], silently: Boolean): AnyRef = {
      jsonValue match {
        case number: Number => conversion.fromInt(number.intValue(), schema, schema.getLogicalType)
        case str: String => Decoder[LocalTime].decodeJson(fromString(str)).fold(throw _, identity)
        case other => other
      }
    }
  }

  object TimeMicrosConverter extends AvroTypeConverter {
    private val conversion = new TimeConversions.TimeMicrosConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.LONG && schema.getLogicalType == LogicalTypes.timeMicros()

    override def convert(field: Schema.Field, schema: Schema, jsonValue: AnyRef, path: util.Deque[String], silently: Boolean): AnyRef = {
      jsonValue match {
        case number: Number => conversion.fromLong(number.intValue(), schema, schema.getLogicalType)
        case str: String => Decoder[LocalTime].decodeJson(fromString(str)).fold(throw _, identity)
        case other => other
      }
    }
  }

  object TimestampMillisConverter extends AvroTypeConverter {
    private val conversion = new TimeConversions.TimestampMillisConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.LONG && schema.getLogicalType == LogicalTypes.timestampMillis()

    override def convert(field: Schema.Field, schema: Schema, jsonValue: AnyRef, path: util.Deque[String], silently: Boolean): AnyRef = {
      jsonValue match {
        case number: Number => conversion.fromLong(number.intValue(), schema, schema.getLogicalType)
        case str: String => Decoder[Instant].decodeJson(fromString(str)).fold(throw _, identity)
        case other => other
      }
    }
  }

  object TimestampMicrosConverter extends AvroTypeConverter {
    private val conversion = new TimeConversions.TimestampMicrosConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.LONG && schema.getLogicalType == LogicalTypes.timestampMicros()

    override def convert(field: Schema.Field, schema: Schema, jsonValue: AnyRef, path: util.Deque[String], silently: Boolean): AnyRef = {
      jsonValue match {
        case number: Number => conversion.fromLong(number.intValue(), schema, schema.getLogicalType)
        case str: String => Decoder[Instant].decodeJson(fromString(str)).fold(throw _, identity)
        case other => other
      }
    }
  }

  object UUIDConverter extends AvroTypeConverter {
    private val conversion = new UUIDConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.STRING && schema.getLogicalType == LogicalTypes.uuid()

    override def convert(field: Schema.Field, schema: Schema, jsonValue: AnyRef, path: util.Deque[String], silently: Boolean): AnyRef = {
      jsonValue match {
        case str: String => conversion.fromCharSequence(str, schema, schema.getLogicalType)
        case other => other
      }
    }
  }

  object DecimalConverter extends AvroTypeConverter {
    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      (schema.getType == Schema.Type.FIXED || schema.getType == Schema.Type.BYTES) &&
        schema.getLogicalType.isInstanceOf[LogicalTypes.Decimal]

    override def convert(field: Schema.Field, schema: Schema, jsonValue: AnyRef, path: util.Deque[String], silently: Boolean): AnyRef = {
      val scale = schema.getLogicalType.asInstanceOf[LogicalTypes.Decimal].getScale
      jsonValue match {
        case bigDecimal: java.math.BigDecimal =>
          bigDecimal.setScale(scale, RoundingMode.DOWN)
        case number: Number =>
          val decimal = new java.math.BigDecimal(number.toString)
          decimal.setScale(scale, RoundingMode.DOWN)
        case str: String =>
          val decimal = new java.math.BigDecimal(str)
          decimal.setScale(scale, RoundingMode.DOWN)
        case other => other
      }
    }
  }

}