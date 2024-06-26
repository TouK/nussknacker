package pl.touk.nussknacker.engine.kafka

import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, TopicName}
import pl.touk.nussknacker.engine.kafka.validator.CachedTopicsExistenceValidator

object KafkaComponentsUtils extends KafkaUtils {

  def validateTopicsExistence[T <: TopicName](topics: List[PreparedKafkaTopic[T]], kafkaConfig: KafkaConfig): Unit = {
    new CachedTopicsExistenceValidator(kafkaConfig = kafkaConfig)
      .validateTopics(topics.map(_.prepared))
      .valueOr(err => throw err)
  }

  def prepareKafkaTopic[T <: TopicName](
      topic: T,
      modelDependencies: ProcessObjectDependencies
  ): PreparedKafkaTopic[T] = {
    val doPrepareName: String => String = (name: String) => modelDependencies.namingStrategy.prepareName(name)
    (topic match {
      case TopicName.OfSource(name) =>
        PreparedKafkaTopic(TopicName.OfSource(name), TopicName.OfSource(doPrepareName(name)))
      case TopicName.OfSink(name) => PreparedKafkaTopic(TopicName.OfSink(name), TopicName.OfSink(doPrepareName(name)))
    }).asInstanceOf[PreparedKafkaTopic[T]]
  }

}
