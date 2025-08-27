package pl.touk.nussknacker.engine.kafka.source.flink

import cats.data.NonEmptyList
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.{LazyParameter, Params}
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, Source, TopicName}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsConfig, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaTestParametersInfo
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalToJsonFormatter
import pl.touk.nussknacker.engine.schemedkafka.source.KafkaSourceImplFactory

import java.lang.{Long => JLong}

class FlinkKafkaSourceImplFactory[K, V] extends KafkaSourceImplFactory[K, V] with Serializable {

  override def createSource(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Any,
      preparedTopics: NonEmptyList[PreparedKafkaTopic[TopicName.ForSource]],
      kafkaComponentsConfig: KafkaComponentsConfig,
      deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
      formatter: UniversalToJsonFormatter[K, V],
      contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
      testParametersInfo: KafkaTestParametersInfo,
      namingStrategy: NamingStrategy,
      eventTimeParameter: LazyParameter[JLong]
  ): Source =
    new FlinkKafkaSource[K, V](
      preparedTopics,
      kafkaComponentsConfig,
      deserializationSchema,
      formatter,
      contextInitializer,
      testParametersInfo,
      namingStrategy,
      eventTimeParameter
    )

}
