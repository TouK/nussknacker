package pl.touk.nussknacker.engine.kafka

import pl.touk.nussknacker.engine.api.namespaces.{KafkaUsageKey, NamingContext}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.kafka.validator.CachedTopicsExistenceValidator

object KafkaComponentsUtils extends KafkaUtils {

  final val KafkaTopicUsageKey = new NamingContext(KafkaUsageKey)

  def validateTopicsExistence(topics: List[PreparedKafkaTopic], kafkaConfig: KafkaConfig): Unit = {
    new CachedTopicsExistenceValidator(kafkaConfig = kafkaConfig)
      .validateTopics(topics.map(_.prepared)).valueOr(err => throw err)
  }

  def prepareKafkaTopic(topic :String, processObjectDependencies: ProcessObjectDependencies): PreparedKafkaTopic =
    PreparedKafkaTopic(
      topic,
      processObjectDependencies
        .objectNaming
        .prepareName(topic, processObjectDependencies.config, KafkaTopicUsageKey)
    )

}
