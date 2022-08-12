package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.{contain, convertToAnyShouldWrapper}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming

class LiteKafkaComponentProviderTest extends AnyFunSuite {

  test("should add low level kafka components by default") {
    val provider = new LiteKafkaComponentProvider
    val config: Config = ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef("not_used"))

    val components = provider.create(config, ProcessObjectDependencies(config, DefaultNamespacedObjectNaming))

    components.size shouldBe 11
  }

  test("should not add low level kafka components when disabled") {
    val provider = new LiteKafkaComponentProvider
    val config: Config = ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef("not_used"))
      .withValue("kafka.lowLevelComponentsEnabled", fromAnyRef(false))

    val components = provider.create(config, ProcessObjectDependencies(config, DefaultNamespacedObjectNaming))

    components.size shouldBe 2
  }
}
