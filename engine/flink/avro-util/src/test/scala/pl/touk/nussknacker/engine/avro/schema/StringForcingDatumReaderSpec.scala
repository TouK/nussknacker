package pl.touk.nussknacker.engine.avro.schema

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import org.apache.avro.generic.{GenericDatumWriter, GenericRecord}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.apache.avro.Schema
import org.apache.avro.util.Utf8
import org.scalatest.{EitherValues, FunSpec, Matchers}
import pl.touk.nussknacker.engine.avro.{AvroStringSettingsInTests, AvroUtils, LogicalTypesGenericRecordBuilder}

class StringForcingDatumReaderSpec extends FunSpec with Matchers with EitherValues {

  it("should encode & decode") {
    val schema = wrapWithRecordSchema(
      """[
        |  { "name": "foo", "type": "string" }
        |]""".stripMargin)
    val builder = new LogicalTypesGenericRecordBuilder(schema)
    builder.set("foo", "bar")
    val givenRecord = builder.build()

    val readRecordWithUtf = AvroStringSettingsInTests.withStringEnabled(setting = false) {
      roundTripWriteRead(givenRecord)
    }
    readRecordWithUtf.get("foo") shouldBe a[Utf8]

    val readWhenStringForced = roundTripWriteRead(readRecordWithUtf)
    readWhenStringForced.get("foo") shouldBe a[String]
    readWhenStringForced shouldEqual givenRecord
  }

  it("should use correct type in provided default value") {
    val schema = wrapWithRecordSchema(
      """[
        |  { "name": "foo", "type": "string", "default": "bar" }
        |]""".stripMargin)

    val record1 = new LogicalTypesGenericRecordBuilder(schema).build()
    record1.get("foo") shouldBe a[String]

    val record2 = AvroStringSettingsInTests.withStringEnabled(setting = false) {
      new LogicalTypesGenericRecordBuilder(schema).build()
    }
    record2.get("foo") shouldBe a[Utf8]
  }

  private def wrapWithRecordSchema(fieldsDefinition: String) =
    new Schema.Parser().parse(
      s"""{
         |  "name": "sample",
         |  "type": "record",
         |  "fields": $fieldsDefinition
         |}""".stripMargin)

  private def roundTripWriteRead(givenRecord: GenericRecord): GenericRecord = {
    val bos = new ByteArrayOutputStream()
    val encoder = EncoderFactory.get().binaryEncoder(bos, null)
    val schema = givenRecord.getSchema
    new GenericDatumWriter[GenericRecord](schema, AvroUtils.genericData).write(givenRecord, encoder)
    encoder.flush()
    val decoder = DecoderFactory.get().binaryDecoder(new ByteArrayInputStream(bos.toByteArray), null)
    val readRecord = StringForcingDatumReaderProvider.genericDatumReader[GenericRecord](schema, schema, AvroUtils.genericData)
      .read(null, decoder)
    readRecord shouldBe givenRecord
    readRecord
  }

}
