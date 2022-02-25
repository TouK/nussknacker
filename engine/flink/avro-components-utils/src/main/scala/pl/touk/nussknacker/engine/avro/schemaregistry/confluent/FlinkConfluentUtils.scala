package pl.touk.nussknacker.engine.avro.schemaregistry.confluent

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.specific.SpecificRecordBase
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.formats.avro.typeutils.{LogicalTypesAvroTypeInfo, LogicalTypesGenericRecordAvroTypeInfo, LogicalTypesGenericRecordWithSchemaIdAvroTypeInfo}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.GenericRecordSchemaIdSerializationSupport
import pl.touk.nussknacker.engine.avro.{AvroUtils, RuntimeSchemaData}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.reflect.{ClassTag, classTag}

// TODO: This class is not used now, but should be used in our TypeInformation mechanisms (for messages passed between operators and for managed stated)
object FlinkConfluentUtils extends LazyLogging {

  def typeInfoForSchema[T: ClassTag](kafkaConfig: KafkaConfig, schemaDataOpt: Option[RuntimeSchemaData]): TypeInformation[T] = {
    val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    val isSpecificRecord = AvroUtils.isSpecificRecord[T]

    schemaDataOpt match {
      case Some(schemaData) if !isSpecificRecord && GenericRecordSchemaIdSerializationSupport.schemaIdSerializationEnabled(kafkaConfig) =>
        logger.debug("Using LogicalTypesGenericRecordWithSchemaIdAvroTypeInfo for GenericRecord serialization")
        val schemaId = schemaData.schemaIdOpt.getOrElse(throw new IllegalStateException("SchemaId serialization enabled but schemaId missed from reader schema data"))
        new LogicalTypesGenericRecordWithSchemaIdAvroTypeInfo(schemaData.schema, schemaId).asInstanceOf[TypeInformation[T]]
      case Some(schemaData) if !isSpecificRecord =>
        logger.debug("Using LogicalTypesGenericRecordAvroTypeInfo for GenericRecord serialization")
        new LogicalTypesGenericRecordAvroTypeInfo(schemaData.schema).asInstanceOf[TypeInformation[T]]
      case _ if isSpecificRecord => // For specific records we ignoring version because we have exact schema inside class
        new LogicalTypesAvroTypeInfo(clazz.asInstanceOf[Class[_ <: SpecificRecordBase]]).asInstanceOf[TypeInformation[T]]
      case _ =>
        // Is type info is correct for non-specific-record case? We can't do too much more without schema.
        TypeInformation.of(clazz)
    }
  }
}
