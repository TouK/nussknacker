package pl.touk.nussknacker.engine.schemedkafka.schema

import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericDatumReader}

trait StringForcingDatumReader[T] extends GenericDatumReader[T] {
  self: GenericDatumReader[T] =>

  override def findStringClass(schema: Schema): Class[_] = {
    // This method is invoked e.g. when determining class for map key
    if (schema.getType == Schema.Type.STRING || schema.getType == Schema.Type.MAP) classOf[String]
    else super.findStringClass(schema)
  }

}

object StringForcingDatumReaderProvider {
  def genericDatumReader[T](writer: Schema, reader: Schema, data: GenericData): GenericDatumReader[T] =
    new GenericDatumReader[T](writer, reader, data) with StringForcingDatumReader[T]
}

/**
  * `object StringForcingDatumReader` doesn't cooperate with Java well
  */
class StringForcingDatumReaderProvider[T] {
  def genericDatumReader(writer: Schema, reader: Schema, data: GenericData): GenericDatumReader[T] =
    StringForcingDatumReaderProvider.genericDatumReader[T](writer, reader, data)
}
