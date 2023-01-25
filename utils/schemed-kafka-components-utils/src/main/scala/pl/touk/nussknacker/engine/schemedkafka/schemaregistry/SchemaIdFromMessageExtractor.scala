package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import org.apache.kafka.common.header.Headers

import java.nio.ByteBuffer
import scala.collection.compat.immutable.LazyList

trait SchemaIdFromMessageExtractor {

  final def getSchemaId(headers: Headers, data: Array[Byte], isKey: Boolean): Option[SchemaIdWithPositionedBuffer] =
    getSchemaId(GetSchemaIdArgs(headers, data, isKey))

  private[schemedkafka] def getSchemaId(args: GetSchemaIdArgs): Option[SchemaIdWithPositionedBuffer]

}

case class SchemaIdWithPositionedBuffer(value: SchemaId, buffer: ByteBuffer) {
  def bufferStartPosition: Int = buffer.position()
}

case class GetSchemaIdArgs(headers: Headers, data: Array[Byte], isKey: Boolean)

class ChainedSchemaIdFromMessageExtractor(chain: List[SchemaIdFromMessageExtractor]) extends SchemaIdFromMessageExtractor with Serializable {
  override private[schemedkafka] def getSchemaId(args: GetSchemaIdArgs): Option[SchemaIdWithPositionedBuffer] = {
    chain.to(LazyList)
      .map(f => f.getSchemaId(args))
      .collectFirst {
        case Some(v) => v
      }
  }

  def withFallbackSchemaId(fallback: Option[SchemaId]) = new ChainedSchemaIdFromMessageExtractor(chain :+ new UseFallbackSchemaId(fallback))

}

private class UseFallbackSchemaId(fallback: Option[SchemaId]) extends SchemaIdFromMessageExtractor {
  override private[schemedkafka] def getSchemaId(args: GetSchemaIdArgs): Option[SchemaIdWithPositionedBuffer] = {
    fallback.map((_, ByteBuffer.wrap(args.data))).map(SchemaIdWithPositionedBuffer.apply _ tupled)
  }
}
