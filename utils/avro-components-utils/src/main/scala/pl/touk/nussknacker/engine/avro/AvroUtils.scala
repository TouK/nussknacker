package pl.touk.nussknacker.engine.avro

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.Conversions.{DecimalConversion, UUIDConversion}
import org.apache.avro.Schema
import org.apache.avro.data.TimeConversions
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.avro.io.DatumReader
import org.apache.avro.reflect.ReflectData
import org.apache.avro.specific.{SpecificData, SpecificRecord}
import pl.touk.nussknacker.engine.avro.schema.StringForcingDatumReaderProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.GenericRecordWithSchemaId

import scala.reflect.{ClassTag, classTag}

object AvroUtils extends LazyLogging {

  import collection.JavaConverters._

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

  // Copy from LogicalTypesAvroFactory
  def extractAvroSpecificSchema(clazz: Class[_]): Schema = {
    tryExtractAvroSchemaViaInstance(clazz).getOrElse(specificData.getSchema(clazz))
  }

  private def tryExtractAvroSchemaViaInstance(clazz: Class[_]) =
    try {
      val instance = clazz.getDeclaredConstructor().newInstance().asInstanceOf[SpecificRecord]
      Option(instance.getSchema)
    } catch {
      case e@(_: InstantiationException | _: IllegalAccessException) =>
        logger.warn("Could not extract schema from Avro-generated SpecificRecord class {}: {}.", clazz, e)
        None
    }

  /**
    * It's a simply mapper scala Map[String, Any] to Avro GenericRecord
    */
  def createRecord(schema: Schema, data: collection.Map[String, Any]): GenericRecord = {
    def createValue(value: Any, schema: Schema): Any = {
      def schemaContainsType(typ: Schema.Type) = schema.getType == typ ||
        schema.getType == Schema.Type.UNION && schema.getTypes.asScala.map(_.getType).contains(typ)
      value match {
        case map: collection.Map[String@unchecked, _] if schemaContainsType(Schema.Type.RECORD) =>
          createRecord(schema, map)
        case collection: Traversable[_] if schemaContainsType(Schema.Type.ARRAY) =>
          collection.map(createValue(_, schema)).toList.asJava
        case map: collection.Map[String@unchecked, _] if schemaContainsType(Schema.Type.MAP) =>
          map.mapValues(createValue(_, schema)).asJava
        case (_, _) => value
      }
    }

    val builder = new LogicalTypesGenericRecordBuilder(schema)
    data.foreach{ case (key, value) =>

      val valueToSet = Option(schema.getField(key))
        .map(field => createValue(value, field.schema()))
        .getOrElse(throw new IllegalArgumentException(s"Unknown field $key in schema $schema."))

      builder.set(key, valueToSet)
    }
    builder.build()
  }

  def isLogicalType[T: ClassTag](schema: Schema): Boolean = {
    val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    schema.getLogicalType != null && clazz.isAssignableFrom(schema.getLogicalType.getClass)
  }

}
