package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.GenericRecordWithSchemaId

class GenericRecordSchemaIdSerializationSupport(schemaIdSerializationEnabled: Boolean) {

  def wrapWithRecordWithSchemaIdIfNeeded(data: AnyRef, readerSchemaData: RuntimeSchemaData[AvroSchema]): AnyRef = {
    data match {
      case genericRecord: GenericData.Record if schemaIdSerializationEnabled =>
        val readerSchemaId = readerSchemaData.schemaIdOpt.getOrElse(
          throw new IllegalStateException("SchemaId serialization enabled but schemaId missed from reader schema data")
        )
        new GenericRecordWithSchemaId(genericRecord, readerSchemaId, false)
      case _ => data
    }
  }

}

object GenericRecordSchemaIdSerializationSupport extends LazyLogging {

  def apply(kafkaComponentsConfig: KafkaComponentsConfig): GenericRecordSchemaIdSerializationSupport = {
    new GenericRecordSchemaIdSerializationSupport(schemaIdSerializationEnabled(kafkaComponentsConfig))
  }

  def schemaIdSerializationEnabled(kafkaComponentsConfig: KafkaComponentsConfig): Boolean = {
    val result = Option(kafkaComponentsConfig)
      .flatMap(_.avroKryoGenericRecordSchemaIdSerialization)
      .getOrElse(true)
    logger.debug(s"schemaIdSerializationEnabled: $result")
    result
  }

}
