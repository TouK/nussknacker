package pl.touk.nussknacker.engine.kafka

import pl.touk.nussknacker.engine.api.process.TopicName

// Topic name without specialization (it's mean that we don't know yet if this is topic name used in context of source or sink)
final case class UnspecializedTopicName(name: String)

object UnspecializedTopicName {

  implicit class ToUnspecializedTopicName(val topicName: TopicName) extends AnyVal {

    def toUnspecialized: UnspecializedTopicName = UnspecializedTopicName(
      topicName match {
        case TopicName.ForSource(name) => name
        case TopicName.ForSink(name)   => name
      }
    )

  }

}
