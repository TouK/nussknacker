package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsConfig, OptimizedGenericRecordSerializationConfig}
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.GenericRecordWithSchemaId

trait GenericRecordSchemaIdSerializationSupport {
  def wrapWithRecordWithSchemaIdIfNeeded(data: AnyRef, readerSchemaData: RuntimeSchemaData[AvroSchema]): AnyRef
}

object GenericRecordSchemaIdSerializationSupport extends LazyLogging {

  def apply(kafkaComponentsConfig: KafkaComponentsConfig): GenericRecordSchemaIdSerializationSupport = {
    // we create Avro deserializer even when there is no Schema Registry at all (so no GenericRecord will appear here, ever)
    if (isEnabledForComponent(kafkaComponentsConfig)) {
      val schemaRegistryUrl = kafkaComponentsConfig.kafkaProperties("schema.registry.url")
      new Wrapping(kafkaComponentsConfig.optimizedGenericRecordSerialization.toValidConfig(schemaRegistryUrl))
    } else {
      new Identity()
    }
  }

  def isEnabledForComponent(kafkaComponentsConfig: KafkaComponentsConfig): Boolean =
    kafkaComponentsConfig.optimizedGenericRecordSerialization.enabled &&
      kafkaComponentsConfig.kafkaProperties.contains("schema.registry.url")

  // noinspection ScalaWeakerAccess
  class Wrapping(serializationConfig: OptimizedGenericRecordSerializationConfig)
      extends GenericRecordSchemaIdSerializationSupport {

    override def wrapWithRecordWithSchemaIdIfNeeded(
        data: AnyRef,
        readerSchemaData: RuntimeSchemaData[AvroSchema]
    ): AnyRef = {
      data match {
        case genericRecord: GenericData.Record if serializationConfig.enabled =>
          val readerSchemaId = readerSchemaData.schemaIdOpt.getOrElse(
            throw new IllegalStateException(
              "SchemaId serialization enabled but schemaId is missing in reader schema data"
            )
          )
          new GenericRecordWithSchemaId(genericRecord, serializationConfig.schemaRegistryId, readerSchemaId, false)
        case _ => data
      }
    }

  }

  // noinspection ScalaWeakerAccess
  class Identity extends GenericRecordSchemaIdSerializationSupport {

    override def wrapWithRecordWithSchemaIdIfNeeded(
        data: AnyRef,
        readerSchemaData: RuntimeSchemaData[AvroSchema]
    ): AnyRef =
      data

  }

}
