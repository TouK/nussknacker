package pl.touk.nussknacker.engine.flink.test.docker

import com.dimafeng.testcontainers.SchemaRegistryContainer
import org.scalatest.Suite
import pl.touk.nussknacker.test.containers.WithDockerContainers

trait WithSchemaRegistryContainer { self: Suite with WithDockerContainers with WithKafkaContainer =>

  protected val schemaRegistryContainer: SchemaRegistryContainer =
    SchemaRegistryContainer(network, kafkaNetworkAlias)

  // testcontainers exposes schema registry via mapped port on host network, it will be used for kafkaClient in tests, signal sending etc.
  protected def hostSchemaRegistryUrl: String = schemaRegistryContainer.schemaUrl

}
