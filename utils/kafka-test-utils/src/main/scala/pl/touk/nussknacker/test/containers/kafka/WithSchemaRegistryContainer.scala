package pl.touk.nussknacker.test.containers.kafka

import org.scalatest.Suite
import pl.touk.nussknacker.test.containers.WithDockerContainers

import java.util.Arrays.asList

trait WithSchemaRegistryContainer { self: Suite with WithDockerContainers =>

  private val schemaRegistryNetworkAlias = "schema-registry"

  protected val schemaRegistryContainer: SchemaRegistryContainer =
    SchemaRegistryContainer().configure { self =>
      self.setNetwork(network)
      self.setNetworkAliases(asList(schemaRegistryNetworkAlias))
      self.withLogConsumer(logConsumer(prefix = schemaRegistryNetworkAlias))
    }

}
