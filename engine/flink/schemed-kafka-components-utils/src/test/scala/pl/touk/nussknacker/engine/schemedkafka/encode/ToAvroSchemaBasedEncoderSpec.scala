package pl.touk.nussknacker.engine.schemedkafka.encode

import cats.data.ValidatedNel
import org.apache.avro.{AvroRuntimeException, Schema}
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.avro.generic.GenericData.{EnumSymbol, Fixed}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.schemedkafka.schema.{Address, Company, FullNameV1}
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

import java.nio.charset.StandardCharsets
import java.time._
import java.util.UUID
import scala.collection.immutable.ListSet

class ToAvroSchemaBasedEncoderSpec extends AnyFunSpec with Matchers with EitherValuesDetailedMessage {

  import scala.jdk.CollectionConverters._

  final protected val avroEncoder = ToAvroSchemaBasedEncoder(ValidationMode.strict)

  it("should create simple record") {
    val schema = wrapWithRecordSchema("""[
        |  { "name": "foo", "type": "string" }
        |]""".stripMargin)

    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("foo" -> "bar").asJava, schema))
    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("foo" -> Some("bar")), schema))
  }

  it("should create nested record") {
    val schema = wrapWithRecordSchema("""[
        |  {
        |    "name": "nested",
        |    "type": {
        |      "name": "nested",
        |      "type": "record",
        |      "fields": [
        |         { "name": "foo", "type": "string" }
        |      ]
        |    }
        |  }
        |]""".stripMargin)

    assertThrows[AvroRuntimeException] {
      avroEncoder.encodeRecordOrError(Map("nested" -> Map("foo1" -> "bar").asJava).asJava, schema)
    }

    assertThrows[AvroRuntimeException] {
      avroEncoder.encodeRecordOrError(Map("nested" -> Map("foo" -> "bar", "foo1" -> "bar1").asJava).asJava, schema)
    }

    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("nested" -> Map("foo" -> "bar").asJava).asJava, schema))
  }

  it("should create record with enum field") {
    val enumSchemaStr = """{
                          |  "name": "enum",
                          |  "type": "enum",
                          |  "symbols": ["A", "B", "C"]
                          |}""".stripMargin
    val schema = wrapWithRecordSchema(s"""[
         |  {
         |    "name": "enum",
         |    "type": $enumSchemaStr
         |  }
         |]""".stripMargin)

    val enumSchema = new Schema.Parser().parse(enumSchemaStr)

    assertThrows[AvroRuntimeException] {
      avroEncoder.encodeRecordOrError(Map("enum" -> "X").asJava, schema)
    }

    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("enum" -> "B").asJava, schema))
    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("enum" -> new EnumSymbol(enumSchema, "B")).asJava, schema))
  }

  it("should create record with array field") {
    val schema = wrapWithRecordSchema("""[
        |  {
        |    "name": "array",
        |    "type": {
        |      "type": "array",
        |      "items": "string"
        |    }
        |  }
        |]""".stripMargin)

    assertThrows[AvroRuntimeException] {
      avroEncoder.encodeRecordOrError(Map("array" -> List(1, 2, "foo").asJava).asJava, schema)
    }

    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("array" -> List("foo", "bar").asJava).asJava, schema))
    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("array" -> List("foo", "bar")), schema))
    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("array" -> ListSet("foo", "bar")), schema))
  }

  it("should create record with map field") {
    val schema = wrapWithRecordSchema("""[
        |  {
        |    "name": "map",
        |    "type": {
        |      "type": "map",
        |      "values": "int"
        |    }
        |  }
        |]""".stripMargin)

    assertThrows[AvroRuntimeException] {
      avroEncoder.encodeRecordOrError(Map("map" -> Map("foo" -> "bar").asJava).asJava, schema)
    }

    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("map" -> Map("foo" -> 1).asJava).asJava, schema))
    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("map" -> Map("foo" -> 1)), schema))
  }

  it("should create record with fixed field") {
    val schema = wrapWithRecordSchema("""[
        |  {
        |    "name": "fixed",
        |    "type": {
        |      "type": "fixed",
        |      "name": "fixed",
        |      "size": 3
        |    }
        |  }
        |]""".stripMargin)

    val badAla = "ala123"
    assertThrows[AvroRuntimeException] {
      avroEncoder.encodeRecordOrError(Map("fixed" -> badAla).asJava, schema)
    }

    assertThrows[AvroRuntimeException] {
      avroEncoder.encodeRecordOrError(
        Map("fixed" -> new Fixed(schema, badAla.getBytes(StandardCharsets.UTF_8))).asJava,
        schema
      )
    }

    val goodAla = "ala"
    roundTripVerifyWriteRead(
      avroEncoder.encodeRecord(
        Map("fixed" -> new Fixed(schema, goodAla.getBytes(StandardCharsets.UTF_8))).asJava,
        schema
      )
    )
    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("fixed" -> goodAla).asJava, schema))
  }

  it("should allow to create record with nested GenericRecord field") {
    val data = Map("name" -> Company.DefaultName, "address" -> Address.record)

    roundTripVerifyWriteRead(avroEncoder.encodeRecord(data.asJava, Company.schema))
  }

  it("should not allow to create record with nested GenericRecord field") {
    val data = Map("name" -> Company.DefaultName, "address" -> FullNameV1.record)

    assertThrows[AvroRuntimeException] {
      avroEncoder.encodeRecordOrError(data.asJava, Company.schema)
    }
  }

  it("should create record with union field") {
    val schema = wrapWithRecordSchema("""[
        |  {
        |    "name": "foo",
        |    "type": ["null", "int"]
        |  }
        |]""".stripMargin)

    assertThrows[AvroRuntimeException] {
      avroEncoder.encodeRecordOrError(Map("foo" -> "ala").asJava, schema)
    }

    val recordWithNull = roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("foo" -> null).asJava, schema))
    recordWithNull.get("foo") shouldBe null

    val recordWithNone = roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("foo" -> None), schema))
    recordWithNull.get("foo") shouldBe null

    val recordWithInt = roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("foo" -> 123).asJava, schema))
    recordWithInt.get("foo") shouldBe 123
  }

  it("should accept redundant parameters if validation modes allows this") {
    val schema = wrapWithRecordSchema("""[
        |  { "name": "foo", "type": "string" }
        |]""".stripMargin)

    ToAvroSchemaBasedEncoder(ValidationMode.strict)
      .encodeRecord(Map("foo" -> "bar", "redundant" -> 15).asJava, schema) shouldBe Symbol("invalid")
    ToAvroSchemaBasedEncoder(ValidationMode.lax)
      .encodeRecord(Map("foo" -> "bar", "redundant" -> 15).asJava, schema) shouldBe Symbol("valid")
  }

  it("should create record with logical type for timestamp-millis") {
    checkLogicalType("long", "timestamp-millis", Instant.ofEpochMilli(123L), Instant.ofEpochMilli(123L))
    checkLogicalType(
      "long",
      "timestamp-millis",
      Instant.ofEpochMilli(123L).atOffset(ZoneOffset.ofHours(2)),
      Instant.ofEpochMilli(123L)
    )
    checkLogicalType(
      "long",
      "timestamp-millis",
      Instant.ofEpochMilli(123L).atZone(ZoneId.of("Europe/Warsaw")),
      Instant.ofEpochMilli(123L)
    )
    checkLogicalType("long", "timestamp-millis", Instant.ofEpochSecond(0, 1123000L), Instant.ofEpochSecond(0, 1000000L))
  }

  it("should create record with logical type for timestamp-micros") {
    checkLogicalType("long", "timestamp-micros", Instant.ofEpochSecond(0, 123000L), Instant.ofEpochSecond(0, 123000L))
    checkLogicalType(
      "long",
      "timestamp-micros",
      Instant.ofEpochSecond(0, 123000L).atOffset(ZoneOffset.ofHours(2)),
      Instant.ofEpochSecond(0, 123000L)
    )
    checkLogicalType(
      "long",
      "timestamp-micros",
      Instant.ofEpochSecond(0, 123000L).atZone(ZoneId.of("Europe/Warsaw")),
      Instant.ofEpochSecond(0, 123000L)
    )
  }

  it("should create record with logical type for time-millis") {
    checkLogicalType("int", "time-millis", LocalTime.ofNanoOfDay(123000000L), LocalTime.ofNanoOfDay(123000000L))
    checkLogicalType("int", "time-millis", LocalTime.ofNanoOfDay(1123000L), LocalTime.ofNanoOfDay(1000000L))
  }

  it("should create record with logical type for date") {
    checkLogicalType("int", "time-millis", LocalTime.ofNanoOfDay(123000000L), LocalTime.ofNanoOfDay(123000000L))
  }

  it("should create record with logical type for time-micros") {
    checkLogicalType("int", "date", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 1))
  }

  it("should create record with logical type for decimal") {
    val schema = wrapWithRecordSchema(s"""[
         |  { "name": "foo", "type": {
         |    "type": "bytes",
         |    "logicalType": "decimal",
         |    "precision": 8,
         |    "scale": 2
         |  }}
         |]""".stripMargin)
    encodeRecordWithSingleFieldAndVerify(schema, 123L, new java.math.BigDecimal("123.00"))
    encodeRecordWithSingleFieldAndVerify(schema, 123.45, new java.math.BigDecimal("123.45"))
    encodeRecordWithSingleFieldAndVerify(schema, new java.math.BigDecimal("123.45"), new java.math.BigDecimal("123.45"))
    encodeRecordWithSingleFieldAndVerify(
      schema,
      new java.math.BigDecimal("123.456"),
      new java.math.BigDecimal("123.45")
    )
  }

  it("should create record with logical type for uuid") {
    val uuid = UUID.randomUUID()
    checkLogicalType("string", "uuid", uuid, uuid)
    checkLogicalType("string", "uuid", uuid.toString, uuid)

    val uuidSchema = wrapWithRecordSchema(s"""[
         |  { "name": "foo", "type": {
         |    "type": "string",
         |    "logicalType": "uuid"
         |  }}
         |]""".stripMargin)

    val notUuid = "not-uuid"
    val thrown = the[AvroRuntimeException] thrownBy {
      avroEncoder.encodeRecordOrError(Map("foo" -> notUuid).asJava, uuidSchema)
    }

    thrown.getMessage shouldBe s"Value '$notUuid' is not a UUID."
  }

  it("should return logical type default value") {
    val schema = wrapWithRecordSchema(s"""[
         |  { "name": "foo", "type": {
         |    "type": "long",
         |    "logicalType": "timestamp-millis"
         |  }, "default": 0 }
         |]""".stripMargin)

    val encoded       = avroEncoder.encodeRecord(Map.empty[String, Any], schema)
    val encodedRecord = encoded.toEither.rightValue
    encodedRecord.get("foo") shouldEqual Instant.ofEpochMilli(0)
  }

  private def checkLogicalType(underlyingType: String, logicalType: String, givenValue: Any, expectedValue: Any) = {
    val schema = wrapWithRecordSchema(s"""[
         |  { "name": "foo", "type": {
         |    "type": "$underlyingType",
         |    "logicalType": "$logicalType"
         |  }}
         |]""".stripMargin)
    encodeRecordWithSingleFieldAndVerify(schema, givenValue, expectedValue)
  }

  private def encodeRecordWithSingleFieldAndVerify(schema: Schema, givenValue: Any, expectedValue: Any) = {
    val encoded       = avroEncoder.encodeRecord(Map("foo" -> givenValue).asJava, schema)
    val encodedRecord = encoded.toEither.rightValue
    val readRecord    = roundTripWriteRead(encodedRecord)
    readRecord.get("foo") shouldEqual expectedValue
  }

  private def wrapWithRecordSchema(fieldsDefinition: String) =
    new Schema.Parser().parse(s"""{
                                 |  "name": "sample",
                                 |  "type": "record",
                                 |  "fields": $fieldsDefinition
                                 |}""".stripMargin)

  private def roundTripVerifyWriteRead(givenRecordVal: ValidatedNel[String, GenericData.Record]) = {
    val givenRecord = givenRecordVal.toEither.rightValue
    val readRecord  = roundTripWriteRead(givenRecord)
    readRecord shouldEqual givenRecord
    readRecord
  }

  private def roundTripWriteRead(givenRecord: GenericRecord) = {
    val bytes = AvroUtils.serializeContainerToBytesArray(givenRecord)
    AvroUtils.deserialize[GenericRecord](bytes, givenRecord.getSchema)
  }

}
