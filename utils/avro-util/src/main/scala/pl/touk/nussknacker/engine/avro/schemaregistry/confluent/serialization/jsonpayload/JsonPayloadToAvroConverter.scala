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
import tech.allegro.schema.json2avro.converter.{CompositeJsonToAvroReader, JsonAvroConverter, PathsPrinter, UnknownFieldListener}

import java.math.RoundingMode
import java.time.{Instant, LocalDate, LocalTime}
import java.util
import scala.collection.JavaConverters._
import scala.util.Try

class JsonPayloadToAvroConverter(specificClass: Option[Class[SpecificRecordBase]]) {

  // To make schema evolution works correctly we need to turn off default FailOnUnknownField listener
  // (to handle situation when some field was removed from schema but exists in messages)
  object DumbUnknownFieldListener extends UnknownFieldListener {
    override def onUnknownField(name: String, value: Any, path: String): Unit = {}
  }

  private val converter = new JsonAvroConverter(new CompositeJsonToAvroReader(List[AvroTypeConverter](
    DateConverter, TimeMillisConverter, TimeMicrosConverter, TimestampMillisConverter, TimestampMicrosConverter,
    UUIDConverter, DecimalConverter
  ).asJava, DumbUnknownFieldListener))

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

    override val expectedFormat: String = "'yyyy-MM-dd' or number of epoch days"

  }

  object TimeMillisConverter extends BaseAvroTypeConverter {

    private val conversion = new TimeConversions.TimeMillisConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.INT && schema.getLogicalType == LogicalTypes.timeMillis()

    override def convertPF(schema: Schema, path: util.Deque[String], silently: Boolean): PartialFunction[AnyRef, AnyRef] = {
      case number: Number => tryConvert(path, silently)(conversion.fromInt(number.intValue(), schema, schema.getLogicalType))
      case str: String => Decoder[LocalTime].decodeJson(fromString(str)).toValue(path, silently)
    }

    override def expectedFormat: String = "'HH:mm:ss.SSS' or number of millis of day"

  }

  object TimeMicrosConverter extends BaseAvroTypeConverter {

    private val conversion = new TimeConversions.TimeMicrosConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.LONG && schema.getLogicalType == LogicalTypes.timeMicros()

    override def convertPF(schema: Schema, path: util.Deque[String], silently: Boolean): PartialFunction[AnyRef, AnyRef] = {
      case number: Number => tryConvert(path, silently)(conversion.fromLong(number.intValue(), schema, schema.getLogicalType))
      case str: String => Decoder[LocalTime].decodeJson(fromString(str)).toValue(path, silently)
    }

    override def expectedFormat: String = "'HH:mm:ss.SSSSSS' or number of micros of day"

  }

  object TimestampMillisConverter extends BaseAvroTypeConverter {

    private val conversion = new TimeConversions.TimestampMillisConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.LONG && schema.getLogicalType == LogicalTypes.timestampMillis()

    override def convertPF(schema: Schema, path: util.Deque[String], silently: Boolean): PartialFunction[AnyRef, AnyRef] = {
      case number: Number => tryConvert(path, silently)(conversion.fromLong(number.intValue(), schema, schema.getLogicalType))
      case str: String => Decoder[Instant].decodeJson(fromString(str)).toValue(path, silently)
    }

    override def expectedFormat: String = "'yyyy-MM-dd`T`HH:mm:ss.SSSZ' or number of epoch millis"

  }

  object TimestampMicrosConverter extends BaseAvroTypeConverter {

    private val conversion = new TimeConversions.TimestampMicrosConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.LONG && schema.getLogicalType == LogicalTypes.timestampMicros()

    override def convertPF(schema: Schema, path: util.Deque[String], silently: Boolean): PartialFunction[AnyRef, AnyRef] = {
      case number: Number => tryConvert(path, silently)(conversion.fromLong(number.intValue(), schema, schema.getLogicalType))
      case str: String => Decoder[Instant].decodeJson(fromString(str)).toValue(path, silently)
    }

    override def expectedFormat: String = "'yyyy-MM-dd`T`HH:mm:ss.SSSSSSZ' or number of epoch micros"

  }

  object UUIDConverter extends BaseAvroTypeConverter {

    private val conversion = new UUIDConversion

    override def canManage(schema: Schema, path: util.Deque[String]): Boolean =
      schema.getType == Schema.Type.STRING && schema.getLogicalType == LogicalTypes.uuid()

    override def convertPF(schema: Schema, path: util.Deque[String], silently: Boolean): PartialFunction[AnyRef, AnyRef] = {
      case str: String => tryConvert(path, silently)(conversion.fromCharSequence(str, schema, schema.getLogicalType))
    }

    override def expectedFormat: String = "UUID"

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

    override def expectedFormat: String = "decimal"

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
        throw new AvroRuntimeException(s"Field: ${PathsPrinter.print(path)} is expected to has $expectedFormat format", cause.orNull)

  }

}