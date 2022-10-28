package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.test.KafkaConfigProperties

class FlinkKafkaComponentProviderTest extends AnyFunSuite {

  test("should add low level kafka components when enabled") {
    val provider = new FlinkKafkaComponentProvider
    val config: Config = ConfigFactory.load()
      .withValue(KafkaConfigProperties.bootstrapServersProperty("config"), fromAnyRef("not_used"))
      .withValue("config.lowLevelComponentsEnabled", fromAnyRef(true))

    val components = provider.create(config, ProcessObjectDependencies(config, DefaultNamespacedObjectNaming))

    components.size shouldBe 4
  }

  test("should not add low level kafka components by default") {
    val provider = new FlinkKafkaComponentProvider
    val config: Config = ConfigFactory.load()
      .withValue(KafkaConfigProperties.bootstrapServersProperty("config"), fromAnyRef("not_used"))

    val components = provider.create(config, ProcessObjectDependencies(config, DefaultNamespacedObjectNaming))

    components.size shouldBe 2
  }
}
