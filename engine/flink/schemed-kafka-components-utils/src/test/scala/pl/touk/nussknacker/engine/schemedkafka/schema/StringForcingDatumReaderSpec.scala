package pl.touk.nussknacker.engine.schemedkafka.schema

import org.apache.avro.Schema
import org.apache.avro.generic.{GenericDatumWriter, GenericRecord}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.schemedkafka.{AvroUtils, LogicalTypesGenericRecordBuilder}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class StringForcingDatumReaderSpec extends AnyFunSpec with Matchers {

  it("should encode & decode") {
    val schema = wrapWithRecordSchema("""[
        |  { "name": "foo", "type": "string" }
        |]""".stripMargin)
    val builder = new LogicalTypesGenericRecordBuilder(schema)
    builder.set("foo", "bar")
    val givenRecord = builder.build()
    givenRecord.get("foo") shouldBe a[String]

    val readWhenStringForced = roundTripWriteRead(givenRecord)

    readWhenStringForced.get("foo") shouldBe a[String]
    readWhenStringForced shouldEqual givenRecord
  }

  private def wrapWithRecordSchema(fieldsDefinition: String) =
    new Schema.Parser().parse(s"""{
         |  "name": "sample",
         |  "type": "record",
         |  "fields": $fieldsDefinition
         |}""".stripMargin)

  private def roundTripWriteRead(givenRecord: GenericRecord): GenericRecord = {
    val bos     = new ByteArrayOutputStream()
    val encoder = EncoderFactory.get().binaryEncoder(bos, null)
    val schema  = givenRecord.getSchema
    new GenericDatumWriter[GenericRecord](schema, AvroUtils.genericData).write(givenRecord, encoder)
    encoder.flush()
    val decoder = DecoderFactory.get().binaryDecoder(new ByteArrayInputStream(bos.toByteArray), null)
    val readRecord = StringForcingDatumReaderProvider
      .genericDatumReader[GenericRecord](schema, schema, AvroUtils.genericData)
      .read(null, decoder)
    readRecord shouldBe givenRecord
    readRecord
  }

}
