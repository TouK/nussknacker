package pl.touk.nussknacker.engine.avro

import org.apache.avro.Conversions.{DecimalConversion, UUIDConversion}
import org.apache.avro.Schema
import org.apache.avro.data.TimeConversions
import org.apache.avro.generic.GenericData
import org.apache.avro.io.DatumReader
import org.apache.avro.reflect.ReflectData
import org.apache.avro.specific.{SpecificData, SpecificRecord}
import pl.touk.nussknacker.engine.avro.schema.StringForcingDatumReaderProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.GenericRecordWithSchemaId

import scala.reflect.{ClassTag, classTag}

object AvroUtils {

  def isSpecificRecord[T: ClassTag]: Boolean = {
    val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    isSpecificRecord(clazz)
  }

  def isSpecificRecord(clazz: Class[_]): Boolean = {
    classOf[SpecificRecord].isAssignableFrom(clazz)
  }

  def genericData: GenericData = addLogicalTypeConversions(new GenericData(_) {
    override def deepCopy[T](schema: Schema, value: T): T = {
      val copiedRecord = super.deepCopy(schema, value)
      value match {
        case withSchemaId: GenericRecordWithSchemaId =>
          new GenericRecordWithSchemaId(copiedRecord.asInstanceOf[GenericData.Record], withSchemaId.getSchemaId, false).asInstanceOf[T]
        case _ => copiedRecord
      }
    }

    override def createDatumReader(writer: Schema, reader: Schema): DatumReader[_] = StringForcingDatumReaderProvider
      .genericDatumReader(writer, reader, this.asInstanceOf[GenericData])

    override def createDatumReader(schema: Schema): DatumReader[_] = createDatumReader(schema, schema)
  })

  def specificData: SpecificData = addLogicalTypeConversions(new SpecificData(_) {
    override def createDatumReader(writer: Schema, reader: Schema): DatumReader[_] = StringForcingDatumReaderProvider
      .specificDatumReader(writer, reader, this.asInstanceOf[SpecificData])

    override def createDatumReader(schema: Schema): DatumReader[_] = createDatumReader(schema, schema)
  })

  def reflectData: ReflectData = addLogicalTypeConversions(new ReflectData(_) {
    override def createDatumReader(writer: Schema, reader: Schema): DatumReader[_] = StringForcingDatumReaderProvider
      .reflectDatumReader(writer, reader, this.asInstanceOf[ReflectData])

    override def createDatumReader(schema: Schema): DatumReader[_] = createDatumReader(schema, schema)

  })

  private def addLogicalTypeConversions[T <: GenericData](createData: ClassLoader => T): T = {
    val data = createData(Thread.currentThread.getContextClassLoader)
    data.addLogicalTypeConversion(new TimeConversions.DateConversion)
    data.addLogicalTypeConversion(new TimeConversions.TimeMicrosConversion)
    data.addLogicalTypeConversion(new TimeConversions.TimeMillisConversion)
    data.addLogicalTypeConversion(new TimeConversions.TimestampMicrosConversion)
    data.addLogicalTypeConversion(new TimeConversions.TimestampMillisConversion)
    data.addLogicalTypeConversion(new UUIDConversion)
    data.addLogicalTypeConversion(new DecimalConversion)
    data
  }

  private def parser = new Schema.Parser()

  private def parserNotValidatingDefaults = new Schema.Parser().setValidateDefaults(false)

  def parseSchema(avroSchema: String): Schema =
    parser.parse(avroSchema)

  // It is need because regards to that https://github.com/confluentinc/schema-registry/issues/1293 someone
  // could register schema with invalid default in lower avro version and despite this in newer version we want to read it
  def nonRestrictiveParseSchema(avroSchema: String): Schema =
    parserNotValidatingDefaults.parse(avroSchema)

  def wrapWithGenericRecordWithSchemaIdIfDefined[T](record: T, nullableSchemaId: Integer): T = {
    (record, Option(nullableSchemaId)) match {
      case (genericRecord: GenericData.Record, Some(schemaId)) => new GenericRecordWithSchemaId(genericRecord, schemaId, false).asInstanceOf[T]
      case _ => record
    }
  }

}
