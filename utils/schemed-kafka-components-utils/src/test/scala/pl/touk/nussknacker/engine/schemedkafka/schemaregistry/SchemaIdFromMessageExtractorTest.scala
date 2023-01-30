package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.kafka.KafkaRecordUtils.{emptyHeaders, toHeaders}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.schemaid.SchemaIdFromNuHeadersPotentiallyShiftingConfluentPayload.{KeySchemaIdHeaderName, ValueSchemaIdHeaderName}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaBasedSerdeProvider

import java.io.ByteArrayOutputStream

class SchemaIdFromMessageExtractorTest extends AnyFunSuite with Matchers with OptionValues {

  private val extractor = UniversalSchemaBasedSerdeProvider.schemaIdFromMessageExtractor

  test("extract schema id from nu specific headers") {
    extractor.getSchemaId(
      toHeaders(KeySchemaIdHeaderName -> "123"), Array.empty, isKey = true).value.value shouldEqual SchemaId.fromInt(123)
    extractor.getSchemaId(
      toHeaders(ValueSchemaIdHeaderName -> "123"), Array.empty, isKey = false).value.value shouldEqual SchemaId.fromInt(123)
  }

  test("extract confluent schema id from payload") {
    val bos = new ByteArrayOutputStream()
    val schemaId = SchemaId.fromInt(123)
    ConfluentUtils.writeSchemaId(schemaId, bos)
    bos.flush()
    val resultForKey = extractor.getSchemaId(emptyHeaders, bos.toByteArray, isKey = true).value
    resultForKey.value shouldEqual schemaId
    resultForKey.buffer.array() shouldEqual Array(ConfluentUtils.MagicByte, 0, 0, 0, schemaId.asInt.byteValue())
    resultForKey.buffer.position() shouldEqual ConfluentUtils.HeaderSize
    val resultForValue = extractor.getSchemaId(emptyHeaders, bos.toByteArray, isKey = false).value
    resultForValue.value shouldEqual schemaId
    resultForValue.buffer.array() shouldEqual Array(ConfluentUtils.MagicByte, 0, 0, 0, schemaId.asInt.byteValue())
    resultForKey.buffer.position() shouldEqual ConfluentUtils.HeaderSize
  }

  test("schema id from headers is more important than schema id from payload") {
    val schemaIdFromHeader = SchemaId.fromInt(123)
    val headersWithSchemaIdAvailable = toHeaders(ValueSchemaIdHeaderName -> schemaIdFromHeader.toString)
    val bos = new ByteArrayOutputStream()
    ConfluentUtils.writeSchemaId(SchemaId.fromInt(234), bos)
    bos.flush()
    extractor.getSchemaId(headersWithSchemaIdAvailable, bos.toByteArray, isKey = false).value.value shouldEqual schemaIdFromHeader
    extractor.getSchemaId(headersWithSchemaIdAvailable, Array.empty, isKey = false).value.value shouldEqual schemaIdFromHeader
  }

  test("even if schema id appear in headers, schema id from payload should be read and position in buffer should be moved") {
    val schemaIdInBothHeaderAndPayload = SchemaId.fromInt(123)
    val headersWithSchemaIdAvailable = toHeaders(ValueSchemaIdHeaderName -> schemaIdInBothHeaderAndPayload.toString)
    val bos = new ByteArrayOutputStream()
    ConfluentUtils.writeSchemaId(schemaIdInBothHeaderAndPayload, bos)
    bos.flush()
    val result = extractor.getSchemaId(headersWithSchemaIdAvailable, bos.toByteArray, isKey = false).value
    result.value shouldEqual schemaIdInBothHeaderAndPayload
    result.buffer.array() shouldEqual Array(ConfluentUtils.MagicByte, 0, 0, 0, schemaIdInBothHeaderAndPayload.asInt.byteValue())
    result.buffer.position() shouldEqual ConfluentUtils.HeaderSize
  }

}
