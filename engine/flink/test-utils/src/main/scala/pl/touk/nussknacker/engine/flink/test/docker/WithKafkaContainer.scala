package pl.touk.nussknacker.engine.flink.test.docker

import com.dimafeng.testcontainers.KafkaContainer
import org.scalatest.Suite
import org.testcontainers.utility.DockerImageName
import pl.touk.nussknacker.test.containers.WithDockerContainers

import java.util.Arrays.asList

trait WithKafkaContainer { self: Suite with WithDockerContainers =>

  protected val kafkaNetworkAlias = "kafka"

  protected val kafkaContainer: KafkaContainer =
    KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.2")).configure { self =>
      // can segfault on startup, we need retries - https://issues.apache.org/jira/browse/KAFKA-20314
      self.withStartupAttempts(3)
      self.setNetwork(network)
      self.withLogConsumer(logConsumer(prefix = "kafka"))
      self.setNetworkAliases(asList(kafkaNetworkAlias))
    }

  // testcontainers expose kafka via mapped port on host network, it will be used for kafkaClient in tests, signal sending etc.
  protected def hostKafkaAddress: String = kafkaContainer.bootstrapServers

  // on flink we have to access kafka via network alias
  protected def dockerKafkaAddress = s"$kafkaNetworkAlias:9093"

}
