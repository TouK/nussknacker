package pl.touk.nussknacker.engine.kafka

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.namespaces.NamespaceContext
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, TopicName}
import pl.touk.nussknacker.engine.kafka.validator.CachedTopicsExistenceValidator
import pl.touk.nussknacker.engine.kafka.validator.TopicsExistenceValidator.TopicValidationType

object KafkaComponentsUtils extends KafkaUtils {

  def validateTopicsExistence[T <: TopicName: TopicValidationType](
      topics: NonEmptyList[PreparedKafkaTopic[T]],
      kafkaConfig: KafkaConfig
  ): Unit = {
    new CachedTopicsExistenceValidator(kafkaConfig = kafkaConfig)
      .validateTopics(topics.map(_.prepared))
      .valueOr(err => throw err)
  }

  def prepareKafkaTopic[T <: TopicName](
      topic: T,
      modelDependencies: ProcessObjectDependencies
  ): PreparedKafkaTopic[T] = {
    val doPrepareName: String => String = (name: String) =>
      modelDependencies.namingStrategy.prepareName(name, NamespaceContext.Kafka)
    (topic match {
      case TopicName.ForSource(name) =>
        PreparedKafkaTopic(TopicName.ForSource(name), TopicName.ForSource(doPrepareName(name)))
      case TopicName.ForSink(name) =>
        PreparedKafkaTopic(TopicName.ForSink(name), TopicName.ForSink(doPrepareName(name)))
    }).asInstanceOf[PreparedKafkaTopic[T]]
  }

}
