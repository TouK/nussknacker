package pl.touk.nussknacker.engine.kafka

import pl.touk.nussknacker.engine.api.process.TopicName

final case class UncategorizedTopicName(name: String)

object UncategorizedTopicName {

  implicit class ToUncategorizedTopicName(val topicName: TopicName) extends AnyVal {

    def toUncategorizedTopicName: UncategorizedTopicName = UncategorizedTopicName(
      topicName match {
        case TopicName.OfSource(name) => name
        case TopicName.OfSink(name)   => name
      }
    )

  }

}
