package pl.touk.nussknacker.engine.kafka

import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.process.TopicName

object KafkaComponentsUtils extends KafkaUtils {

  def prepareKafkaTopic[T <: TopicName](
      topic: T,
      modelConfig: ModelConfig
  ): PreparedKafkaTopic[T] = {
    val doPrepareName: String => String = (name: String) => modelConfig.namingStrategy.prepareName(name)
    (topic match {
      case TopicName.ForSource(name) =>
        PreparedKafkaTopic(TopicName.ForSource(name), TopicName.ForSource(doPrepareName(name)))
      case TopicName.ForSink(name) =>
        PreparedKafkaTopic(TopicName.ForSink(name), TopicName.ForSink(doPrepareName(name)))
    }).asInstanceOf[PreparedKafkaTopic[T]]
  }

}
