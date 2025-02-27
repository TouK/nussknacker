package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.schemaid

import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  GetSchemaIdArgs,
  SchemaIdFromMessageExtractor,
  SchemaIdWithPositionedBuffer
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.AzureUtils

import java.nio.ByteBuffer

object SchemaIdFromAzureHeader extends SchemaIdFromMessageExtractor {

  override private[schemedkafka] def getSchemaId(args: GetSchemaIdArgs): Option[SchemaIdWithPositionedBuffer] = {
    if (args.isKey) {
      None // Azure schema registry doesn't support keys serialized in Avro format
    } else {
      AzureUtils.extractSchemaIdOpt(args.headers).map { schemaId =>
        SchemaIdWithPositionedBuffer(schemaId, ByteBuffer.wrap(args.data))
      }
    }
  }

}
