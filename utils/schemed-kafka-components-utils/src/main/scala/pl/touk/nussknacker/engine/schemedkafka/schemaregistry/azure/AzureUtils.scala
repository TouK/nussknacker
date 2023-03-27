package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.header.internals.RecordHeader
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId

import java.nio.charset.StandardCharsets

object AzureUtils {

  private val contentTypeHeader = "content-type"

  def extractSchemaId(headers: Headers): SchemaId = {
    extractSchemaIdOpt(headers).getOrElse(throw new IllegalArgumentException(contentTypeHeader + " header not available"))
  }

  def extractSchemaIdOpt(headers: Headers): Option[SchemaId] = {
    val contentTypeHeaderOpt = headers.lastHeader(contentTypeHeader)
    Option(contentTypeHeaderOpt).map { contentTypeHeader =>
      val contentTypeHeaderString = new String(contentTypeHeader.value(), StandardCharsets.UTF_8)
      require(contentTypeHeaderString.startsWith("avro/binary+"), s"$contentTypeHeader header: '$contentTypeHeaderString' should start with avro/binary")
      SchemaId.fromString(contentTypeHeaderString.replaceFirst("avro/binary\\+", ""))
    }
  }

  def avroContentTypeHeader(schemaId: SchemaId) =
    new RecordHeader(contentTypeHeader, s"avro/binary+${schemaId.asString}".getBytes(StandardCharsets.UTF_8))

}
