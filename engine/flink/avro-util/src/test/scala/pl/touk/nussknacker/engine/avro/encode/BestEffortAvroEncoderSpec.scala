package pl.touk.nussknacker.engine.avro.encode

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import cats.data.ValidatedNel
import org.apache.avro.generic.{GenericData, GenericDatumReader, GenericDatumWriter, GenericRecord}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.apache.avro.{AvroRuntimeException, Schema}
import org.scalatest.{FunSpec, Matchers}
import pl.touk.nussknacker.engine.avro.schema.{Address, Company, FullNameV1}

import scala.collection.immutable.ListSet

class BestEffortAvroEncoderSpec extends FunSpec with Matchers {

  import collection.JavaConverters._

  final protected val avroEncoder = BestEffortAvroEncoder()

  it("should create simple record") {
    val schema = wrapWithRecordSchema(
      """[
        |  { "name": "foo", "type": "string" }
        |]""".stripMargin)

    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("foo" -> "bar").asJava, schema))
    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("foo" -> Some("bar")), schema))
  }

  it("should create nested record") {
    val schema = wrapWithRecordSchema(
      """[
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
    val schema = wrapWithRecordSchema(
      """[
        |  {
        |    "name": "enum",
        |    "type": {
        |      "name": "enum",
        |      "type": "enum",
        |      "symbols": ["A", "B", "C"]
        |    }
        |  }
        |]""".stripMargin)

    assertThrows[AvroRuntimeException] {
      avroEncoder.encodeRecordOrError(Map("enum" -> "X").asJava, schema)
    }

    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("enum" -> "B").asJava, schema))
  }

  it("should create record with array field") {
    val schema = wrapWithRecordSchema(
      """[
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
    val schema = wrapWithRecordSchema(
      """[
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
    val schema = wrapWithRecordSchema(
      """[
        |  {
        |    "name": "fixed",
        |    "type": {
        |      "type": "fixed",
        |      "name": "fixed",
        |      "size": 3
        |    }
        |  }
        |]""".stripMargin)

    assertThrows[AvroRuntimeException] {
      avroEncoder.encodeRecordOrError(Map("fixed" -> "ala123").asJava, schema)
    }

    roundTripVerifyWriteRead(avroEncoder.encodeRecord(Map("fixed" -> "ala").asJava, schema))
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
    val schema = wrapWithRecordSchema(
      """[
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

  private def wrapWithRecordSchema(fieldsDefinition: String) =
    new Schema.Parser().parse(s"""{
       |  "name": "sample",
       |  "type": "record",
       |  "fields": $fieldsDefinition
       |}""".stripMargin)

  private def roundTripVerifyWriteRead(givenRecordVal: ValidatedNel[String, GenericData.Record]) = {
    val givenRecord = givenRecordVal.toOption.get
    val bos = new ByteArrayOutputStream()
    val encoder = EncoderFactory.get().binaryEncoder(bos, null)
    val schema = givenRecord.getSchema
    new GenericDatumWriter[GenericRecord](schema).write(givenRecord, encoder)
    encoder.flush()
    val decoder = DecoderFactory.get().binaryDecoder(new ByteArrayInputStream(bos.toByteArray), null)
    val readRecord = new GenericDatumReader[GenericRecord](schema).read(null, decoder)
    readRecord shouldEqual givenRecord
    readRecord
  }

}
