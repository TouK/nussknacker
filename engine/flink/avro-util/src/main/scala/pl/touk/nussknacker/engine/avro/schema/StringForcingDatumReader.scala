package pl.touk.nussknacker.engine.avro.schema

import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericDatumReader}
import org.apache.avro.reflect.{ReflectData, ReflectDatumReader}
import org.apache.avro.specific.{SpecificData, SpecificDatumReader}

trait StringForcingDatumReader[T] extends GenericDatumReader[T]  {
  self: GenericDatumReader[T] =>

  override def findStringClass(schema: Schema): Class[_] = {
    if (schema.getType == Schema.Type.STRING && AvroStringSettings.forceUsingStringForStringSchema) classOf[String]
    else super.findStringClass(schema)
  }
}

object StringForcingDatumReader {
  def genericDatumReader[T](writer: Schema, reader: Schema, data: GenericData): GenericDatumReader[T] =
    new GenericDatumReader[T](writer, reader, data) with StringForcingDatumReader[T]

  def specificDatumReader[T](writer: Schema, reader: Schema, data: SpecificData): SpecificDatumReader[T] =
    new SpecificDatumReader[T](writer, reader, data) with StringForcingDatumReader[T]

  def reflectiveDatumReader[T](writer: Schema, reader: Schema, data: ReflectData): ReflectDatumReader[T] =
    new ReflectDatumReader[T](writer, reader, data) with StringForcingDatumReader[T]
}