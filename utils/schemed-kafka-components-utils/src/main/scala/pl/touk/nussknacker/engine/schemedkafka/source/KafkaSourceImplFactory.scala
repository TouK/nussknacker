package pl.touk.nussknacker.engine.schemedkafka.source

import cats.data.NonEmptyList
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.Params
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaTestParametersInfo
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalToJsonFormatter

trait KafkaSourceImplFactory[K, V] {

  def createSource(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Any,
      preparedTopics: NonEmptyList[PreparedKafkaTopic[TopicName.ForSource]],
      kafkaConfig: KafkaConfig,
      deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
      formatter: UniversalToJsonFormatter[K, V],
      contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
      testParametersInfo: KafkaTestParametersInfo,
      namingStrategy: NamingStrategy
  ): Source

}
