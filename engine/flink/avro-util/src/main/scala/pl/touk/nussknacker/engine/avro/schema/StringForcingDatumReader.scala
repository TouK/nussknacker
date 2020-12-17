package pl.touk.nussknacker.engine.avro.schema

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericDatumReader}
import org.apache.avro.io.Decoder
import org.apache.avro.reflect.{ReflectData, ReflectDatumReader}
import org.apache.avro.specific.{SpecificData, SpecificDatumReader}
import org.apache.avro.util.Utf8

import scala.util.{Properties, Try}

trait StringForcingDatumReader[T] extends GenericDatumReader[T]  {
  self: GenericDatumReader[T] =>

  override def readString(old: Any, expected: Schema, in: Decoder): AnyRef = super.readString(old, expected, in) match {
    case data: Utf8 if AvroStringSettings.forceUsingStringForStringSchema => data.toString
    case data => data
  }

  override def findStringClass(schema: Schema): Class[_] = {
    if (schema.getType == Schema.Type.STRING && AvroStringSettings.forceUsingStringForStringSchema) classOf[String]
    else super.findStringClass(schema)
  }
}

object AvroStringSettings extends LazyLogging {
  val default: Boolean = false
  val envName = "AVRO_USE_STRING_FOR_STRING_TYPE"

  lazy val forceUsingStringForStringSchema: Boolean = Properties.envOrNone(envName)
    .map(str => Try(str.toBoolean).getOrElse {
      logger.warn(s"$envName is not valid boolean value. Using default $default value")
      default
    }).getOrElse(default)
}

object StringForcingDatumReader {
  def forGenericDatumReader[T](writer: Schema, reader: Schema, data: GenericData): GenericDatumReader[T] =
    new GenericDatumReader[T](writer, reader, data) with StringForcingDatumReader[T]

  def forSpecificDatumReader[T](writer: Schema, reader: Schema, data: SpecificData): SpecificDatumReader[T] =
    new SpecificDatumReader[T](writer, reader, data) with StringForcingDatumReader[T]

  def forReflectiveDatumReader[T](writer: Schema, reader: Schema, data: ReflectData): ReflectDatumReader[T] =
    new ReflectDatumReader[T](writer, reader, data) with StringForcingDatumReader[T]
}