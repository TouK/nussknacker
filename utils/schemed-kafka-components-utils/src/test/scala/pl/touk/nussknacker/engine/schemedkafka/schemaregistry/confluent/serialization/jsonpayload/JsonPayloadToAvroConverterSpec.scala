package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.jsonpayload

import io.circe.Json
import io.circe.Json.{fromDoubleOrNull, fromString}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.scalatest.{Inside, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.schemedkafka.encode.AvroToJsonEncoderCustomisation
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder

import java.nio.charset.StandardCharsets
import java.time.{Instant, LocalDate, LocalTime}
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.util.{Failure, Try}

class JsonPayloadToAvroConverterSpec extends AnyFunSuite with Matchers with OptionValues with Inside {

  val avroToJsonEncoder: PartialFunction[Any, Json] =
    new AvroToJsonEncoderCustomisation().encoder(ToJsonEncoder.defaultForTests.encode)

  test("date logical type") {
    val schema = prepareSchema("""{ "type": "int", "logicalType": "date" }""", defaultOpt = Some("124"))

    val recordWithUnderlyingType = convert("123", schema)
    recordWithUnderlyingType.fieldValue shouldEqual LocalDate.ofEpochDay(123L)
    avroToJsonEncoder(recordWithUnderlyingType).fieldValue shouldEqual fromString("1970-05-04")

    val recordWithFormattedValue = convert("\"1970-05-04\"", schema)
    recordWithFormattedValue.fieldValue shouldEqual LocalDate.ofEpochDay(123L)
    avroToJsonEncoder(recordWithFormattedValue).fieldValue shouldEqual fromString("1970-05-04")

    val recordWithDefaultValue = convertMissingField(schema)
    recordWithDefaultValue.fieldValue shouldEqual LocalDate.ofEpochDay(124L)
  }

  test("invalid format of date logical type") {
    val schema = prepareSchema("""{ "type": "int", "logicalType": "date" }""")
    inside(Try(convert("\"invalid\"", schema))) { case Failure(ex) =>
      ex.getCause should have message "Field field should be a valid date."
    }
  }

  test("union of logical type and other type") {
    val schemaWithNull = prepareSchema("""[ { "type": "int", "logicalType": "date" }, "null"]""")
    convert("null", schemaWithNull).fieldValue shouldBe null

    val schemaWithString = prepareSchema("""[ { "type": "int", "logicalType": "date" }, "string"]""")
    convert("\"string not representing date\"", schemaWithString).fieldValue shouldEqual "string not representing date"
  }

  test("time-millis type") {
    val schema = prepareSchema("""{ "type": "int", "logicalType": "time-millis" }""")

    val recordWithUnderlyingType = convert("123456", schema)
    recordWithUnderlyingType.fieldValue shouldEqual LocalTime.ofNanoOfDay(TimeUnit.MILLISECONDS.toNanos(123456))
    avroToJsonEncoder(recordWithUnderlyingType).fieldValue shouldEqual fromString("00:02:03.456")

    val recordWithFormattedValue = convert("\"00:02:03.456\"", schema)
    recordWithFormattedValue.fieldValue shouldEqual LocalTime.ofNanoOfDay(TimeUnit.MILLISECONDS.toNanos(123456))
    avroToJsonEncoder(recordWithFormattedValue).fieldValue shouldEqual fromString("00:02:03.456")
  }

  test("time-micros type") {
    val schema = prepareSchema("""{ "type": "long", "logicalType": "time-micros" }""")

    val recordWithUnderlyingType = convert("123456", schema)
    recordWithUnderlyingType.fieldValue shouldEqual LocalTime.ofNanoOfDay(TimeUnit.MICROSECONDS.toNanos(123456))
    avroToJsonEncoder(recordWithUnderlyingType).fieldValue shouldEqual fromString("00:00:00.123456")

    val recordWithFormattedValue = convert("\"00:00:00.123456\"", schema)
    recordWithFormattedValue.fieldValue shouldEqual LocalTime.ofNanoOfDay(TimeUnit.MICROSECONDS.toNanos(123456))
    avroToJsonEncoder(recordWithFormattedValue).fieldValue shouldEqual fromString("00:00:00.123456")
  }

  test("timestamp-millis logical type") {
    val schema = prepareSchema("""{ "type": "long", "logicalType": "timestamp-millis" }""")

    val recordWithUnderlyingType = convert("123", schema)
    recordWithUnderlyingType.fieldValue shouldEqual Instant.ofEpochMilli(123L)
    avroToJsonEncoder(recordWithUnderlyingType).fieldValue shouldEqual fromString("1970-01-01T00:00:00.123Z")

    val recordWithFormattedValue = convert("\"1970-01-01T00:00:00.123Z\"", schema)
    recordWithFormattedValue.fieldValue shouldEqual Instant.ofEpochMilli(123L)
    avroToJsonEncoder(recordWithFormattedValue).fieldValue shouldEqual fromString("1970-01-01T00:00:00.123Z")
  }

  test("timestamp-micros logical type") {
    val schema = prepareSchema("""{ "type": "long", "logicalType": "timestamp-micros" }""")

    val recordWithUnderlyingType = convert("123", schema)
    recordWithUnderlyingType.fieldValue shouldEqual Instant.ofEpochSecond(0, 123000L)
    avroToJsonEncoder(recordWithUnderlyingType).fieldValue shouldEqual fromString("1970-01-01T00:00:00.000123Z")

    val recordWithFormattedValue = convert("\"1970-01-01T00:00:00.000123Z\"", schema)
    recordWithFormattedValue.fieldValue shouldEqual Instant.ofEpochSecond(0, 123000L)
    avroToJsonEncoder(recordWithFormattedValue).fieldValue shouldEqual fromString("1970-01-01T00:00:00.000123Z")
  }

  test("uuid logical type") {
    val schema = prepareSchema("""{ "type": "string", "logicalType": "uuid" }""")

    val uuid                     = UUID.fromString("f8a69d18-018a-4a38-93b6-9a2479836b72")
    val recordWithFormattedValue = convert("\"f8a69d18-018a-4a38-93b6-9a2479836b72\"", schema)
    recordWithFormattedValue.fieldValue shouldEqual uuid
    avroToJsonEncoder(recordWithFormattedValue).fieldValue shouldEqual fromString(
      "f8a69d18-018a-4a38-93b6-9a2479836b72"
    )
  }

  test("decimal logical type") {
    val schema = prepareSchema("""{ "type": "bytes", "logicalType": "decimal", "precision": 4, "scale": 2 }""")

    val recordWithNumberType = convert("123.45", schema)
    recordWithNumberType.fieldValue shouldEqual new java.math.BigDecimal("123.45")
    avroToJsonEncoder(recordWithNumberType).fieldValue shouldEqual fromDoubleOrNull(123.45)

    val recordWithStringType = convert("123.45", schema)
    recordWithStringType.fieldValue shouldEqual new java.math.BigDecimal("123.45")
    avroToJsonEncoder(recordWithStringType).fieldValue shouldEqual fromDoubleOrNull(123.45)
  }

  private def prepareSchema(fieldType: String, defaultOpt: Option[String] = None) = {
    new Schema.Parser().parse(s"""{
         |  "name": "sample",
         |  "type": "record",
         |  "fields": [
         |    { "name": "field", "type": $fieldType${defaultOpt.map(d => ", \"default\": " + d).getOrElse("")} }
         |  ]
         |}""".stripMargin)
  }

  private def convert(fieldJsonValue: String, schema: Schema): GenericRecord = {
    JsonPayloadToAvroConverter.convert(s"""{"field": $fieldJsonValue}""".getBytes(StandardCharsets.UTF_8), schema)
  }

  private def convertMissingField(schema: Schema): GenericRecord = {
    JsonPayloadToAvroConverter.convert("{}".getBytes(StandardCharsets.UTF_8), schema)
  }

  implicit class GenericRecordExt(record: GenericRecord) {
    def fieldValue: AnyRef = record.get("field")
  }

  implicit class JsonRecordExt(json: Json) {
    def fieldValue: Json = json.asObject.value("field").value
  }

}
