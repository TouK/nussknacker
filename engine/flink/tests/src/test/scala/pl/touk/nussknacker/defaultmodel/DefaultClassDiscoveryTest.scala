package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.api.process.TestProcessObjectDependenciesProvider
import pl.touk.nussknacker.engine.flink.test.ClassDiscoveryBaseTest
import pl.touk.nussknacker.engine.flink.util.transformer.{FlinkBaseComponentProvider, FlinkKafkaComponentProvider}
import pl.touk.nussknacker.engine.testing.LocalModelData

class DefaultClassDiscoveryTest extends ClassDiscoveryBaseTest {

  protected override val model: LocalModelData = {
    val config = ConfigFactory.parseString("config {}")
    val components =
      FlinkBaseComponentProvider.Components :::
        new FlinkKafkaComponentProvider().create(config, TestProcessObjectDependenciesProvider.empty)

    LocalModelData(config, components, configCreator = new DefaultConfigCreator)
  }

  protected override val outputResource = "/extractedTypes/defaultModel.json"

}
