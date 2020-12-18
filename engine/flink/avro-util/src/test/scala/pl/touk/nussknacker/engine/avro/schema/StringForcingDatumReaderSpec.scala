package pl.touk.nussknacker.engine.avro.schema

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import org.apache.avro.generic.{GenericDatumWriter, GenericRecord}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.apache.avro.Schema
import org.scalatest.{EitherValues, FunSpec, Matchers}
import pl.touk.nussknacker.engine.avro.{AvroStringSettingsInTests, AvroUtils}
import pl.touk.nussknacker.engine.avro.encode.{BestEffortAvroEncoder, ValidationMode}


class StringForcingDatumReaderSpec extends FunSpec with Matchers with EitherValues {

  import collection.JavaConverters._

  final protected val avroEncoder = BestEffortAvroEncoder(ValidationMode.strict)

  it("should create simple record") {
    val schema = wrapWithRecordSchema(
      """[
        |  { "name": "foo", "type": "string" }
        |]""".stripMargin)

    roundTripVerifyWhenStringTypeIsForced(avroEncoder.encodeRecord(Map("foo" -> "bar").asJava, schema).toEither.right.value)
    roundTripVerifyWhenStringTypeIsForced(avroEncoder.encodeRecord(Map("foo" -> Some("bar")), schema).toEither.right.value)
  }

  private def wrapWithRecordSchema(fieldsDefinition: String) =
    new Schema.Parser().parse(
      s"""{
         |  "name": "sample",
         |  "type": "record",
         |  "fields": $fieldsDefinition
         |}""".stripMargin)

  private def roundTripVerifyWhenStringTypeIsForced(givenRecord: GenericRecord): GenericRecord = {
    val read = roundTripVerifyWriteRead(givenRecord)
    val readWhenStringForced = AvroStringSettingsInTests.whenEnabled {
      roundTripVerifyWriteRead(read)
    }
    readWhenStringForced shouldEqual givenRecord
    readWhenStringForced
  }

  private def roundTripVerifyWriteRead(givenRecord: GenericRecord): GenericRecord = {
    val readRecord = roundTripWriteRead(givenRecord)
    readRecord shouldEqual givenRecord
    readRecord
  }

  private def roundTripWriteRead(givenRecord: GenericRecord): GenericRecord = {
    val bos = new ByteArrayOutputStream()
    val encoder = EncoderFactory.get().binaryEncoder(bos, null)
    val schema = givenRecord.getSchema
    new GenericDatumWriter[GenericRecord](schema, AvroUtils.genericData).write(givenRecord, encoder)
    encoder.flush()
    val decoder = DecoderFactory.get().binaryDecoder(new ByteArrayInputStream(bos.toByteArray), null)
    val readRecord = StringForcingDatumReaderProvider.genericDatumReader[GenericRecord](schema, schema, AvroUtils.genericData)
      .read(null, decoder)
    readRecord
  }

}
