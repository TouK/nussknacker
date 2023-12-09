package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.test.KafkaConfigProperties

class LiteKafkaComponentProviderTest extends AnyFunSuite {

  test("should not add low level kafka components by default") {
    val provider = new LiteKafkaComponentProvider
    val config: Config = ConfigFactory
      .load()
      .withValue(KafkaConfigProperties.bootstrapServersProperty(), fromAnyRef("not_used"))

    val components = provider.create(config, ProcessObjectDependencies.empty)

    components.size shouldBe 2
  }

  test("should add low level kafka components when enabled") {
    val provider = new LiteKafkaComponentProvider
    val config: Config = ConfigFactory
      .load()
      .withValue(KafkaConfigProperties.bootstrapServersProperty(), fromAnyRef("not_used"))
      .withValue("kafka.lowLevelComponentsEnabled", fromAnyRef(true))

    val components = provider.create(config, ProcessObjectDependencies(config, ObjectNaming.OriginalNames))

    components.size shouldBe 11
  }

}
