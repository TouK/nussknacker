package pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload

import io.circe.Decoder
import io.circe.Json.fromString
import org.apache.avro.Conversions.UUIDConversion
import org.apache.avro.data.TimeConversions
import org.apache.avro.generic.GenericRecord
import org.apache.avro.specific.SpecificRecordBase
import org.apache.avro.{AvroRuntimeException, LogicalTypes, Schema}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.jsonpayload.JsonPayloadToAvroConverter._
import tech.allegro.schema.json2avro.converter.types.AvroTypeConverter
import tech.allegro.schema.json2avro.converter.{CompositeJsonToAvroReader, JsonAvroConverter, PathsPrinter}

import java.math.RoundingMode
import java.time.{Instant, LocalDate, LocalTime}
import java.util
import scala.util.Try

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

  object DateConverter extends BaseAvroTypeConverter {

    private val conversion = new TimeConversions.DateConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.INT && schema.getLogicalType == LogicalTypes.date()

    override def convertPF(schema: Schema, path: util.Deque[String], silently: Boolean): PartialFunction[AnyRef, AnyRef] = {
      case number: Number => tryConvert(path, silently)(conversion.fromInt(number.intValue(), schema, schema.getLogicalType))
      case str: String => Decoder[LocalDate].decodeJson(fromString(str)).toValue(path, silently)
    }

    override val expectedFormat: String = "TODO"

  }

  object TimeMillisConverter extends BaseAvroTypeConverter {

    private val conversion = new TimeConversions.TimeMillisConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.INT && schema.getLogicalType == LogicalTypes.timeMillis()

    override def convertPF(schema: Schema, path: util.Deque[String], silently: Boolean): PartialFunction[AnyRef, AnyRef] = {
      case number: Number => tryConvert(path, silently)(conversion.fromInt(number.intValue(), schema, schema.getLogicalType))
      case str: String => Decoder[LocalTime].decodeJson(fromString(str)).toValue(path, silently)
    }

    override def expectedFormat: String = "TODO"

  }

  object TimeMicrosConverter extends BaseAvroTypeConverter {

    private val conversion = new TimeConversions.TimeMicrosConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.LONG && schema.getLogicalType == LogicalTypes.timeMicros()

    override def convertPF(schema: Schema, path: util.Deque[String], silently: Boolean): PartialFunction[AnyRef, AnyRef] = {
      case number: Number => tryConvert(path, silently)(conversion.fromLong(number.intValue(), schema, schema.getLogicalType))
      case str: String => Decoder[LocalTime].decodeJson(fromString(str)).toValue(path, silently)
    }

    override def expectedFormat: String = "TODO"

  }

  object TimestampMillisConverter extends BaseAvroTypeConverter {

    private val conversion = new TimeConversions.TimestampMillisConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.LONG && schema.getLogicalType == LogicalTypes.timestampMillis()

    override def convertPF(schema: Schema, path: util.Deque[String], silently: Boolean): PartialFunction[AnyRef, AnyRef] = {
      case number: Number => tryConvert(path, silently)(conversion.fromLong(number.intValue(), schema, schema.getLogicalType))
      case str: String => Decoder[Instant].decodeJson(fromString(str)).toValue(path, silently)
    }

    override def expectedFormat: String = "TODO"

  }

  object TimestampMicrosConverter extends BaseAvroTypeConverter {

    private val conversion = new TimeConversions.TimestampMicrosConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.LONG && schema.getLogicalType == LogicalTypes.timestampMicros()

    override def convertPF(schema: Schema, path: util.Deque[String], silently: Boolean): PartialFunction[AnyRef, AnyRef] = {
      case number: Number => tryConvert(path, silently)(conversion.fromLong(number.intValue(), schema, schema.getLogicalType))
      case str: String => Decoder[Instant].decodeJson(fromString(str)).toValue(path, silently)
    }

    override def expectedFormat: String = "TODO"

  }

  object UUIDConverter extends BaseAvroTypeConverter {

    private val conversion = new UUIDConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.STRING && schema.getLogicalType == LogicalTypes.uuid()

    override def convertPF(schema: Schema, path: util.Deque[String], silently: Boolean): PartialFunction[AnyRef, AnyRef] = {
      case str: String => tryConvert(path, silently)(conversion.fromCharSequence(str, schema, schema.getLogicalType))
    }

    override def expectedFormat: String = "TODO"

  }

  object DecimalConverter extends BaseAvroTypeConverter {

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      (schema.getType == Schema.Type.FIXED || schema.getType == Schema.Type.BYTES) &&
        schema.getLogicalType.isInstanceOf[LogicalTypes.Decimal]

    override def convertPF(schema: Schema, path: util.Deque[String], silently: Boolean): PartialFunction[AnyRef, AnyRef] = {
      case bigDecimal: java.math.BigDecimal =>
        alignDecimalScale(schema, bigDecimal)
      case number: Number =>
        alignDecimalScale(schema, new java.math.BigDecimal(number.toString))
      case str: String =>
        tryConvert(path, silently)(alignDecimalScale(schema, new java.math.BigDecimal(str)))
    }

    override def expectedFormat: String = "TODO"

  }

  private def alignDecimalScale(schema: Schema, bigDecimal: java.math.BigDecimal) = {
    val scale = schema.getLogicalType.asInstanceOf[LogicalTypes.Decimal].getScale
    bigDecimal.setScale(scale, RoundingMode.DOWN)
  }

  trait BaseAvroTypeConverter extends AvroTypeConverter {

    def expectedFormat: String

    override final def convert(field: Schema.Field, schema: Schema, jsonValue: AnyRef, path: util.Deque[String], silently: Boolean): AnyRef = {
      convertPF(schema, path, silently).lift(jsonValue).getOrElse(handleUnexpectedFormat(path, silently, None))
    }

    protected def convertPF(schema: Schema, path: util.Deque[String], silently: Boolean): PartialFunction[AnyRef, AnyRef]

    protected def tryConvert(path: util.Deque[String], silently: Boolean)(doConvert: => AnyRef): AnyRef = {
      Try(doConvert).fold(ex => handleUnexpectedFormat(path, silently, Some(ex)), identity)
    }

    protected implicit class DecoderResultExt[A <: AnyRef](decoderResult: Decoder.Result[A]) {
      def toValue(path: util.Deque[String], silently: Boolean): AnyRef = {
        decoderResult.fold(ex => handleUnexpectedFormat(path, silently, Some(ex)), identity)
      }
    }

    protected def handleUnexpectedFormat(path: util.Deque[String], silently: Boolean, cause: Option[Throwable]): AnyRef =
      if (silently)
        AvroTypeConverter.INCOMPATIBLE
      else
        throw new AvroRuntimeException(s"Field: ${PathsPrinter.print(path)} is expected to has: $expectedFormat format", cause.orNull)

  }

}