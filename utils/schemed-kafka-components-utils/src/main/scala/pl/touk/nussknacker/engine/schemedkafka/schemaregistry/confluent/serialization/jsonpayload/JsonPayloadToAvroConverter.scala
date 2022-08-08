package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload

import io.circe.Decoder
import org.apache.avro.Conversions.UUIDConversion
import org.apache.avro.data.RecordBuilderBase
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.avro.specific.SpecificRecordBase
import org.apache.avro.{AvroRuntimeException, LogicalTypes, Schema}
import pl.touk.nussknacker.engine.schemedkafka.LogicalTypesGenericRecordBuilder
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload.JsonPayloadToAvroConverter._
import tech.allegro.schema.json2avro.converter.types.{AvroTypeConverter, BytesDecimalConverter, IntDateConverter, IntTimeMillisConverter, LongTimeMicrosConverter, LongTimestampMicrosConverter, LongTimestampMillisConverter, RecordConverter}
import tech.allegro.schema.json2avro.converter.{CompositeJsonToAvroReader, JsonAvroConverter, PathsPrinter, UnknownFieldListener}

import java.time.{Instant, LocalDate, LocalTime}
import java.time.format.DateTimeFormatter
import java.util
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.util.Try

class JsonPayloadToAvroConverter(specificClass: Option[Class[SpecificRecordBase]]) {

  private val converter = new JsonAvroConverter(
    new CompositeJsonToAvroReader(List[AvroTypeConverter](
      LogicalTypeIntDateConverter, LogicalTypeIntTimeMillisConverter, LogicalTypeLongTimeMicrosConverter,
      LogicalTypeLongTimestampMillisConverter, LogicalTypeLongTimestampMicrosConverter,
      UUIDConverter, DecimalConverter).asJava) {

      override def createMainConverter(unknownFieldListener: UnknownFieldListener): AvroTypeConverter = {
        new RecordConverter(this, unknownFieldListener) {
          override def createRecordBuilder(schema: Schema): RecordBuilderBase[GenericData.Record] = {
            new LogicalTypesGenericRecordBuilder(schema)
          }
          override def setField(builder: RecordBuilderBase[GenericData.Record], subField: Schema.Field, fieldValue: Any): Unit = {
            builder.asInstanceOf[LogicalTypesGenericRecordBuilder].set(subField, fieldValue)
          }
        }
      }

    }
  )

  def convert(payload: Array[Byte], schema: Schema): GenericRecord = {
    specificClass match {
      case Some(kl) => converter.convertToSpecificRecord(payload, kl, schema)
      case None => converter.convertToGenericDataRecord(payload, schema)
    }
  }

}

object JsonPayloadToAvroConverter {

  object LogicalTypeIntDateConverter extends IntDateConverter(DateTimeFormatter.ISO_DATE) {
    override def convertDateTimeString(dateTimeString: String): AnyRef = parseLocalDate(dateTimeString)

    override def convertNumber(daysFromEpoch: Number): AnyRef = LocalDate.ofEpochDay(daysFromEpoch.intValue())
  }

  object LogicalTypeIntTimeMillisConverter extends IntTimeMillisConverter(DateTimeFormatter.ISO_TIME) {
    override def convertDateTimeString(dateTimeString: String): AnyRef = parseLocalTime(dateTimeString)

    override def convertNumber(millisFromMidnight: Number): AnyRef = LocalTime.ofNanoOfDay(TimeUnit.MILLISECONDS.toNanos(millisFromMidnight.intValue()))
  }

  object LogicalTypeLongTimeMicrosConverter extends LongTimeMicrosConverter(DateTimeFormatter.ISO_TIME) {
    override def convertDateTimeString(dateTimeString: String): AnyRef = parseLocalTime(dateTimeString)

    override def convertNumber(microsFromMidnight: Number): AnyRef = LocalTime.ofNanoOfDay(TimeUnit.MICROSECONDS.toNanos(microsFromMidnight.longValue()))
  }

  object LogicalTypeLongTimestampMillisConverter extends LongTimestampMillisConverter(DateTimeFormatter.ISO_DATE_TIME) {
    override def convertDateTimeString(dateTimeString: String): AnyRef = parseInstant(dateTimeString)

    override def convertNumber(millisFromEpoch: Number): AnyRef = Instant.ofEpochMilli(millisFromEpoch.longValue())
  }

  object LogicalTypeLongTimestampMicrosConverter extends LongTimestampMicrosConverter(DateTimeFormatter.ISO_DATE_TIME) {
    override def convertDateTimeString(dateTimeString: String): AnyRef = parseInstant(dateTimeString)

    override def convertNumber(microsFromEpoch: Number): AnyRef = {
      // copy-paste from TimeConversions.TimestampMicrosConversion
      val epochSeconds = microsFromEpoch.longValue() / 1000000
      val nanoAdjustment = (microsFromEpoch.longValue() % 1000000) * 1000
      Instant.ofEpochSecond(epochSeconds, nanoAdjustment)
    }
  }

  object DecimalConverter extends BytesDecimalConverter {
    override def convertDecimal(value: Any, scale: Int, path: util.Deque[String]): AnyRef =
      bigDecimalWithExpectedScale(value.toString, scale, path)
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
        new AvroTypeConverter.Incompatible(expectedFormat)
      else
        throw new AvroRuntimeException(s"Field: ${PathsPrinter.print(path)} is expected to has $expectedFormat format", cause.orNull)

  }

}