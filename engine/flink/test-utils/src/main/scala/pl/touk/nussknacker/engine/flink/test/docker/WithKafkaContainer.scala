package pl.touk.nussknacker.engine.flink.test.docker

import com.dimafeng.testcontainers.KafkaContainer
import org.scalatest.Suite
import org.testcontainers.utility.DockerImageName
import pl.touk.nussknacker.test.containers.WithDockerContainers

import java.util.Arrays.asList

trait WithKafkaContainer { self: Suite with WithDockerContainers =>

  protected val kafkaNetworkAlias = "kafka"

  protected val kafkaContainer: KafkaContainer =
    KafkaContainer(DockerImageName.parse("apache/kafka-native:3.9.1")).configure { self =>
      self.setNetwork(network)
      self.setNetworkAliases(asList(kafkaNetworkAlias))
    }

  // testcontainers expose kafka via mapped port on host network, it will be used for kafkaClient in tests, signal sending etc.
  protected def hostKafkaAddress: String = kafkaContainer.bootstrapServers

  // on flink we have to access kafka via network alias
  protected def dockerKafkaAddress = s"$kafkaNetworkAlias:9092"

}
