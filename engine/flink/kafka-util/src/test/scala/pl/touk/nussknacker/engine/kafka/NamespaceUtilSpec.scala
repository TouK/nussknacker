package pl.touk.nussknacker.engine.kafka

import org.scalatest.FlatSpec

class NamespaceUtilSpec extends FlatSpec {
  private def namespacedKafkaConfig(namespace: Option[String]) = KafkaConfig("dummyKafkaAddress", None, None, namespace)

  it should "return unaltered topic name" in {
    // prepare
    val topicName = "topic"
    val kafkaConfig = namespacedKafkaConfig(None)

     // act
    val namespacedTopicName = NamespaceUtil.alterTopicNameIfNamespaced(topicName, kafkaConfig)

    // validate
    assert(namespacedTopicName == topicName)
  }

  it should "return topic name with prepended namespace" in {
    // prepare
    val topicName = "topic"
    val namespaceName = "ns"
    val kafkaConfig = namespacedKafkaConfig(Option(namespaceName))

    // act
    val namespacedTopicName = NamespaceUtil.alterTopicNameIfNamespaced(topicName, kafkaConfig)

    // validate
    assert(namespacedTopicName == s"${namespaceName}_$topicName")
  }

}
