package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.header.internals.RecordHeader
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId

import java.nio.charset.StandardCharsets

object AzureUtils {

  def extractSchemaId(headers: Headers): SchemaId = {
    val contentTypeHeader = headers.lastHeader("content-type")
    require(contentTypeHeader != null, "content-type header not available")
    val contentTypeHeaderString = new String(contentTypeHeader.value(), StandardCharsets.UTF_8)
    require(contentTypeHeaderString.startsWith("avro/binary+"), s"content-type header: '$contentTypeHeaderString' should start with avro/binary")
    SchemaId.fromString(contentTypeHeaderString.replaceFirst("avro/binary\\+", ""))
  }

  def avroContentTypeHeader(schemaId: SchemaId) =
    new RecordHeader("content-type", s"avro/binary+${schemaId.asString}".getBytes(StandardCharsets.UTF_8))

}
