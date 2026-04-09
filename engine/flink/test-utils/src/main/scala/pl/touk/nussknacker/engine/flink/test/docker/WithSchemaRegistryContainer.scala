package pl.touk.nussknacker.engine.flink.test.docker

import com.dimafeng.testcontainers.GenericContainer
import org.scalatest.Suite
import pl.touk.nussknacker.test.containers.WithDockerContainers

import java.util.Arrays.asList

trait WithSchemaRegistryContainer { self: Suite with WithDockerContainers with WithKafkaContainer =>

  private val schemaRegistryPort         = 8081
  private val schemaRegistryNetworkAlias = "schema-registry"

  protected val schemaRegistryContainer: GenericContainer =
    new GenericContainer(
      "ghcr.io/axonops/axonops-schema-registry:0.2.1",
      exposedPorts = Seq(schemaRegistryPort),
    ).configure { self =>
      self.setNetwork(network)
      self.setNetworkAliases(asList(schemaRegistryNetworkAlias))
      self.withLogConsumer(logConsumer(prefix = "schema-registry"))
    }

  // testcontainers exposes schema registry via mapped port on host network, it will be used for kafkaClient in tests, signal sending etc.
  protected def hostSchemaRegistryUrl: String =
    s"http://${schemaRegistryContainer.host}:${schemaRegistryContainer.mappedPort(schemaRegistryPort)}"
  protected def containerSchemaRegistryUrl: String = s"http://$schemaRegistryNetworkAlias:$schemaRegistryPort"

}
