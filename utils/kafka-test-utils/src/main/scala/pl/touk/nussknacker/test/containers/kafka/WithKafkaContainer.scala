package pl.touk.nussknacker.test.containers.kafka

import org.scalatest.Suite
import pl.touk.nussknacker.test.containers.WithDockerContainers

import java.util.Arrays.asList

trait WithKafkaContainer { self: Suite with WithDockerContainers =>

  private val kafkaNetworkAlias = "kafka"

  protected val kafkaAutoCreateTopics: Boolean = true

  protected lazy val kafkaContainer: KafkaContainer =
    KafkaContainer().configure { self =>
      self.withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", kafkaAutoCreateTopics.toString.toLowerCase)
      self.setNetwork(network)
      self.withLogConsumer(logConsumer(prefix = kafkaNetworkAlias))
      self.setNetworkAliases(asList(kafkaNetworkAlias))
    }

}
