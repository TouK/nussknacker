package pl.touk.nussknacker.engine.avro

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import org.apache.avro.Conversions.{DecimalConversion, UUIDConversion}
import org.apache.avro.Schema
import org.apache.avro.Schema.Type
import org.apache.avro.data.TimeConversions
import org.apache.avro.generic.GenericData.{EnumSymbol, Fixed}
import org.apache.avro.generic.{GenericContainer, GenericData, GenericRecord}
import org.apache.avro.io.DatumReader
import org.apache.avro.reflect.ReflectData
import org.apache.avro.specific.{SpecificData, SpecificRecord}
import pl.touk.nussknacker.engine.avro.schema.StringForcingDatumReaderProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.GenericRecordWithSchemaId

import java.nio.ByteBuffer
import java.util
import scala.annotation.tailrec
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
    * It's a simply mapper scala Any to Avro Proper Data
    */
  def createAvroData(value: Any, schema: Schema): Any =
    (value, schema.getType) match {
      case (map: collection.Map[String@unchecked, _], Type.RECORD) =>
        createRecord(schema, map)
      case (map: util.Map[String@unchecked, _], Type.RECORD) =>
        createRecord(schema, map.asScala)
      case (collection: Traversable[_], Type.ARRAY) =>
        collection.map(createAvroData(_, schema.getElementType)).toList.asJava
      case (collection: util.Collection[_], Type.ARRAY) =>
        createAvroData(collection.asScala, schema)
      case (map: collection.Map[String@unchecked, _], Type.MAP) =>
        map.mapValues(createAvroData(_, schema.getValueType)).asJava
      case (map: util.Map[String@unchecked, _], Type.MAP) =>
        createAvroData(map.asScala, schema)
      case (str: String, Type.ENUM) =>
        new EnumSymbol(schema, str)
      case (str: String,Type.FIXED) =>
        new Fixed(schema, str.getBytes("UTF-8"))
      case (null, Type.UNION) if schema.isNullable => null
      case (None, Type.UNION) if schema.isNullable => null
      case (bytes: Array[Byte], Type.BYTES) =>
        ByteBuffer.wrap(bytes)
      case (any, Type.UNION) =>
        schema.getTypes.asScala.filterNot(_.isNullable).map(createAvroData(any, _)).headOption.orNull
      case (_, _) => value
    }

  /**
    * It's a simply mapper scala Map[String, Any] to Avro GenericRecord
    */
  def createRecord(schema: Schema, data: scala.collection.Map[String, Any]): GenericRecord = {
    val fields = schema.getFields.asScala.map(_.name()).toSet
    val builder = new LogicalTypesGenericRecordBuilder(schema)

    data
      .filter{ case (key, _) => fields.contains(key) }
      .foreach{ case (key, value) =>
        val field = schema.getField(key)
        val valueToSet = createAvroData(value, field.schema())

        builder.set(key, valueToSet)
      }

    val results = builder.build()
    results
  }

  def verifyLogicalType[T: ClassTag](schema: Schema): Boolean = {
    val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    schema.getLogicalType != null && clazz.isAssignableFrom(schema.getLogicalType.getClass)
  }

  /**
    * Discovering AvoSchema based on data
    */
  def getSchema(data: Any): Schema = {
    def discoverSchema(data: List[Any]) = data.map(getSchema).distinct match {
      case head :: Nil => head
      case list => Schema.createUnion(list.asJava)
    }

    data match {
      case container: GenericContainer =>
        container.getSchema
      case map: java.util.Map[_, _] =>
        val mapValuesSchema = discoverSchema(map.values.asScala.toList)
        Schema.createMap(mapValuesSchema)
      case list: java.util.List[_] =>
        val listValuesSchema = discoverSchema(list.asScala.toList)
        Schema.createArray(listValuesSchema)
      case _ =>
        AvroSchemaUtils.getSchema(data)
    }
  }

}
