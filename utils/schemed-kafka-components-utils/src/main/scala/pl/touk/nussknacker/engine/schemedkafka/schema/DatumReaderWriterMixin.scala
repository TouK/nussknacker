package pl.touk.nussknacker.engine.schemedkafka.schema

import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.DatumReader
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils

/**
  * Mixin for DatumReader and DatumWriter. It collects factory methods for Datums.
  */
trait DatumReaderWriterMixin {

  def createDatumWriter(schema: Schema): GenericDatumWriter[Any] =
    new GenericDatumWriter[Any](schema, AvroUtils.genericData)

  def createDatumReader(writerSchema: Schema, readerSchema: Schema): DatumReader[AnyRef] =
    StringForcingDatumReaderProvider.genericDatumReader[AnyRef](writerSchema, readerSchema, AvroUtils.genericData)

}
