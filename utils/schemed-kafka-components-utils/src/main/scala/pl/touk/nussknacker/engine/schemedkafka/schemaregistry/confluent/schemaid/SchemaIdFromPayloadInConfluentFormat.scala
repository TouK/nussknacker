package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.schemaid

import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{GetSchemaIdArgs, SchemaIdFromMessageExtractor, SchemaIdWithPositionedBuffer}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils

object SchemaIdFromPayloadInConfluentFormat extends SchemaIdFromMessageExtractor {
  override private[schemedkafka] def getSchemaId(args: GetSchemaIdArgs): Option[SchemaIdWithPositionedBuffer] = {
    ConfluentUtils.readIdAndGetBuffer(args.data).toOption.map(SchemaIdWithPositionedBuffer.apply _ tupled)
  }

}
