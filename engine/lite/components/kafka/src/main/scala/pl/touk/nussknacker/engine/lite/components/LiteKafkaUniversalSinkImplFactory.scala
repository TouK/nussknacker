package pl.touk.nussknacker.engine.lite.components

import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.process.{Sink, TopicName}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaSupportDispatcher
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkImplFactory
import pl.touk.nussknacker.engine.util.{KeyedValue, ThreadUtils}

object LiteKafkaUniversalSinkImplFactory extends UniversalKafkaSinkImplFactory {

  override def createSink(
      preparedTopic: PreparedKafkaTopic[TopicName.ForSink],
      keyParam: LazyParameter[AnyRef],
      valueParam: LazyParameter[AnyRef],
      kafkaConfig: KafkaConfig,
      serializationSchema: KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]],
      clientId: String,
      schema: RuntimeSchemaData[ParsedSchema],
      validationMode: ValidationMode
  ): Sink = {
    lazy val encode = UniversalSchemaSupportDispatcher(kafkaConfig)
      .forSchemaType(schema.schema.schemaType())
      .formValueEncoder(schema.schema, validationMode)

    new LazyParamSink[ProducerRecord[Array[Byte], Array[Byte]]] {
      override def prepareResponse: LazyParameter[ProducerRecord[Array[Byte], Array[Byte]]] = {
        keyParam.product(valueParam).map { case (key, value) =>
          // FIXME: we have to make sure ContextClassLoader is set to model classloader in lite
          ThreadUtils.withThisAsContextClassLoader(getClass.getClassLoader) {
            // TODO: timestamp, override topic, clientId, what about other props from KafkaSink?
            serializationSchema.serialize(KeyedValue(key, encode(value)), System.currentTimeMillis())
          }
        }
      }
    }
  }

}
