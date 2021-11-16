package pl.touk.nussknacker.genericmodel

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import pl.touk.nussknacker.engine.flink.test.ClassExtractionBaseTest
import pl.touk.nussknacker.engine.testing.LocalModelData

class GenericClassExtractionTest extends ClassExtractionBaseTest {

  protected override val model: LocalModelData = {
    val config = ConfigFactory.load()
      .withValue("components.kafka.config.kafka.kafkaAddress", fromAnyRef("notused:1111"))
      .withValue("components.kafka.config.kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("notused:1111"))
    LocalModelData(config, new GenericConfigCreator)
  }
  protected override val outputResource = "/extractedTypes/genericCreator.json"

}

