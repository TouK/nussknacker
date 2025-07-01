package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.component.ComponentDependencies
import pl.touk.nussknacker.engine.flink.FlinkBaseUnboundedComponentProvider
import pl.touk.nussknacker.engine.flink.test.ClassDiscoveryBaseTest
import pl.touk.nussknacker.engine.flink.util.transformer.{FlinkBaseComponentProvider, FlinkKafkaComponentProvider}
import pl.touk.nussknacker.engine.testing.LocalModelData

class DefaultClassDiscoveryTest extends ClassDiscoveryBaseTest {

  protected override val model: LocalModelData = {
    val config = ConfigFactory.parseString("config {}")
    val components =
      FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components :::
        new FlinkKafkaComponentProvider()
          .create(config, ComponentDependencies(ModelConfig.parse(ConfigFactory.empty()), designerDbRef = None))

    LocalModelData(config, components, configCreator = new DefaultConfigCreator)
  }

  protected override val outputResource = "/extractedTypes/defaultModel.json"

}
