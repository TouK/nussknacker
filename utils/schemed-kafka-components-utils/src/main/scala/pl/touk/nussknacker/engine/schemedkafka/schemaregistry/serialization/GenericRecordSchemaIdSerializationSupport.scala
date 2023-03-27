package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.GenericRecordWithSchemaId
class GenericRecordSchemaIdSerializationSupport(schemaIdSerializationEnabled: Boolean) {

  def wrapWithRecordWithSchemaIdIfNeeded(data: AnyRef, readerSchemaData: RuntimeSchemaData[AvroSchema]): AnyRef = {
    data match {
      case genericRecord: GenericData.Record if schemaIdSerializationEnabled =>
        val readerSchemaId = readerSchemaData.schemaIdOpt.getOrElse(throw new IllegalStateException("SchemaId serialization enabled but schemaId missed from reader schema data"))
        new GenericRecordWithSchemaId(genericRecord, readerSchemaId, false)
      case _ => data
    }
  }

}

object GenericRecordSchemaIdSerializationSupport extends LazyLogging {

  def apply(kafkaConfig: KafkaConfig): GenericRecordSchemaIdSerializationSupport = {
    new GenericRecordSchemaIdSerializationSupport(schemaIdSerializationEnabled(kafkaConfig))
  }

  def schemaIdSerializationEnabled(kafkaConfig: KafkaConfig): Boolean = {
    val result = Option(kafkaConfig)
      .flatMap(_.avroKryoGenericRecordSchemaIdSerialization)
      .getOrElse(true)
    logger.debug(s"schemaIdSerializationEnabled: $result")
    result
  }

}
