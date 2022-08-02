package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.FunSuite
import org.scalatest.Matchers.convertToAnyShouldWrapper
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming

class FlinkKafkaComponentProviderTest extends FunSuite {

  test("should add low level kafka components by default") {
    val provider = new FlinkKafkaComponentProvider
    val config: Config = ConfigFactory.load()
      .withValue("config.kafkaAddress", fromAnyRef("not_used"))
      .withValue("config.lowLevelComponentsEnabled", fromAnyRef(true))


    val components = provider.create(config, ProcessObjectDependencies(config, DefaultNamespacedObjectNaming))

    components.size shouldBe 11
  }

  test("should not add low level kafka components when disabled") {
    val provider = new FlinkKafkaComponentProvider
    val config: Config = ConfigFactory.load()
      .withValue("config.kafkaAddress", fromAnyRef("not_used"))
      .withValue("config.lowLevelComponentsEnabled", fromAnyRef(false))

    val components = provider.create(config, ProcessObjectDependencies(config, DefaultNamespacedObjectNaming))

    components.size shouldBe 2
  }
}
