package pl.touk.nussknacker.defaultmodel

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import pl.touk.nussknacker.engine.flink.test.ClassExtractionBaseTest
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.KafkaConfigProperties

class DefaultClassExtractionTest extends ClassExtractionBaseTest {

  protected override val model: LocalModelData = {
    val config = ConfigFactory.load()
      .withValue(KafkaConfigProperties.bootstrapServersProperty("components.kafka.config"), fromAnyRef("notused:1111"))
      .withValue(KafkaConfigProperties.property("components.kafka.config", "schema.registry.url"), fromAnyRef("notused:1111"))
    LocalModelData(config, new DefaultConfigCreator)
  }
  protected override val outputResource = "/extractedTypes/defaultModel.json"

}

