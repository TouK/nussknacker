package pl.touk.nussknacker.engine.kafka

object NamespaceUtil {
  def alterTopicNameIfNamespaced(topic: String, config: KafkaConfig): String =
    config.namespace match {
      case Some(value) => s"${value}_${topic}"
      case None => topic
  }
}
