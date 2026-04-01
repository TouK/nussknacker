package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsConfig, OptimizedGenericRecordSerializationConfig}
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.GenericRecordWithSchemaId

class GenericRecordSchemaIdSerializationSupport(serializationConfig: OptimizedGenericRecordSerializationConfig) {

  def wrapWithRecordWithSchemaIdIfNeeded(data: AnyRef, readerSchemaData: RuntimeSchemaData[AvroSchema]): AnyRef = {
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

object GenericRecordSchemaIdSerializationSupport extends LazyLogging {

  def apply(kafkaComponentsConfig: KafkaComponentsConfig): GenericRecordSchemaIdSerializationSupport = {
    val serializationConfig = kafkaComponentsConfig.optimizedGenericRecordSerialization
    new GenericRecordSchemaIdSerializationSupport(
      serializationConfig.toValidConfig(kafkaComponentsConfig.kafkaProperties("schema.registry.url"))
    )
  }

}
