package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import pl.touk.nussknacker.engine.flink.test.ClassExtractionBaseTest
import pl.touk.nussknacker.engine.testing.LocalModelData

class DefaultClassExtractionTest extends ClassExtractionBaseTest {

  protected override val model: LocalModelData = {
    val config = ConfigFactory.load()
      .withValue("components.kafka.config.kafkaAddress", fromAnyRef("notused:1111"))
      .withValue("components.kafka.config.kafkaProperties.\"schema.registry.url\"", fromAnyRef("notused:1111"))
    LocalModelData(config, new DefaultConfigCreator)
  }
  protected override val outputResource = "/extractedTypes/defaultModel.json"

}

