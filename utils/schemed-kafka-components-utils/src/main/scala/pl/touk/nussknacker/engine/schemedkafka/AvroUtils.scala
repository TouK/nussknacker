package pl.touk.nussknacker.engine.schemedkafka

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.serializers.NonRecordContainer
import org.apache.avro.Conversions.{DecimalConversion, UUIDConversion}
import org.apache.avro.Schema
import org.apache.avro.data.TimeConversions
import org.apache.avro.generic.{GenericContainer, GenericData, GenericDatumWriter, GenericRecord}
import org.apache.avro.io.{DatumReader, DecoderFactory, EncoderFactory}
import org.apache.avro.reflect.ReflectData
import org.apache.avro.specific.{SpecificData, SpecificDatumWriter, SpecificRecord}
import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schema.StringForcingDatumReaderProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils.writeSchemaId
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.AvroSchemaWithJsonPayload
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{GenericRecordWithSchemaId, SchemaId}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import java.io.{ByteArrayOutputStream, OutputStream}
import java.nio.ByteBuffer
import java.util
import scala.reflect.{ClassTag, classTag}

object AvroUtils extends LazyLogging {

  import scala.jdk.CollectionConverters._

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

  def adjustParsedSchema(confluentParsedSchema: ParsedSchema, config: SchemaRegistryClientKafkaConfig): ParsedSchema = {
    (confluentParsedSchema, config.avroAsJsonSerialization) match {
      case (schema: AvroSchema, Some(true)) => AvroSchemaWithJsonPayload(schema)
      case _ => confluentParsedSchema
    }
  }

  // It is need because regards to that https://github.com/confluentinc/schema-registry/issues/1293 someone
  // could register schema with invalid default in lower avro version and despite this in newer version we want to read it
  def nonRestrictiveParseSchema(avroSchema: String): Schema =
    parserNotValidatingDefaults.parse(avroSchema)

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


  def serializeContainerToBytesArray(container: GenericContainer): Array[Byte] = {
    val output = new ByteArrayOutputStream()
    try {
      serializeContainerToBytesArray(container, output)
      output.toByteArray
    } finally {
      output.close()
    }
  }

  /**
    * Based on serializeImpl from [[io.confluent.kafka.serializers.AbstractKafkaAvroSerializer]]
    */
  def serializeContainerToBytesArray(container: GenericContainer, output: OutputStream): Unit = {
    val data = container match {
      case non: NonRecordContainer => non.getValue
      case any => any
    }

    data match {
      case v: ByteBuffer =>
        output.write(v.array())
      case v: Array[Byte] =>
        output.write(v)
      case v =>
        val writer = data match {
          case _: SpecificRecord =>
            new SpecificDatumWriter[Any](container.getSchema, AvroUtils.specificData)
          case _ =>
            new GenericDatumWriter[Any](container.getSchema, AvroUtils.genericData)
        }

        val encoder = EncoderFactory.get().binaryEncoder(output, null)
        writer.write(v, encoder)
        encoder.flush()
    }
  }

  def deserialize[T](payload: Array[Byte], readerWriterSchema: Schema, offset: Int = 0): T = {
    if (readerWriterSchema.getType.equals(Schema.Type.BYTES)) {
      util.Arrays.copyOfRange(payload, offset, payload.length).asInstanceOf[T]
    } else {
      val decoder = DecoderFactory.get().binaryDecoder(payload, offset, payload.length - offset, null)
      val reader = StringForcingDatumReaderProvider.genericDatumReader[T](readerWriterSchema, readerWriterSchema, AvroUtils.genericData)
      reader.read(null.asInstanceOf[T], decoder)
    }
  }

  /**
    * It's a simply mapper scala Map[String, Any] to Avro GenericRecord
    */
  def createRecord(schema: Schema, data: collection.Map[String, Any]): GenericRecord = {
    def createValue(value: Any, schema: Schema): Any = {
      class SchemaContainsType(typ: Schema.Type) {
        def unapply(schema: Schema): Option[Schema] = Option(schema).filter(_.getType == typ) orElse
          Option(schema).filter(_.getType == Schema.Type.UNION).flatMap(_.getTypes.asScala.find(_.getType == typ))
      }
      val SchemaContainsRecordSchema = new SchemaContainsType(Schema.Type.RECORD)
      val SchemaContainsArraySchema = new SchemaContainsType(Schema.Type.ARRAY)
      val SchemaContainsMapSchema = new SchemaContainsType(Schema.Type.MAP)

      (value, schema) match {
        case (map: collection.Map[String@unchecked, _], SchemaContainsRecordSchema(recordSchema)) =>
          createRecord(recordSchema, map)
        case (collection: Iterable[_], SchemaContainsArraySchema(arraySchema)) =>
          collection.map(createValue(_, arraySchema.getElementType)).toList.asJava
        case (map: collection.Map[String@unchecked, _], SchemaContainsMapSchema(mapSchema)) =>
          map.toMap.mapValuesNow(createValue(_, mapSchema.getValueType)).asJava
        case _ => value
      }
    }

    val builder = new LogicalTypesGenericRecordBuilder(schema)
    data.foreach { case (key, value) =>
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
