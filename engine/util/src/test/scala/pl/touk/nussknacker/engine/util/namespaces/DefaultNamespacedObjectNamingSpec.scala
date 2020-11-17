package pl.touk.nussknacker.engine.util.namespaces

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.namespaces.{CustomUsageKey, FlinkUsageKey, KafkaUsageKey, NamingContext}

class DefaultNamespacedObjectNamingSpec extends FunSuite with Matchers {

  private val emptyConfig = ConfigFactory.empty()

  private val configWithNamespace = ConfigFactory.empty()
    .withValue(DefaultNamespacedObjectNaming.NamespacePath, fromAnyRef("customer1"))

  private val provider = ObjectNamingProvider(getClass.getClassLoader)

  test("should leave original names if no namespace configured") {

    Array(FlinkUsageKey, KafkaUsageKey, CustomUsageKey("1")).foreach { key =>
      val ctx = new NamingContext(key)

      provider.prepareName("original", emptyConfig, ctx) shouldBe "original"
      provider.objectNamingParameters("original", emptyConfig, ctx) shouldBe None
      provider.decodeName("original", emptyConfig, ctx) shouldBe Some("original")
    }


  }

  test("should add namespace if configured") {

    Array(FlinkUsageKey, KafkaUsageKey, CustomUsageKey("1")).foreach { key =>
      val ctx = new NamingContext(key)

      provider.prepareName("original", configWithNamespace, ctx) shouldBe "customer1_original"
      provider.objectNamingParameters("original", configWithNamespace, ctx) shouldBe
        Some(DefaultNamespacedObjectNamingParameters("original", "customer1"))
      provider.decodeName("customer1_someName", configWithNamespace, ctx) shouldBe Some("someName")
      provider.decodeName("dummy??", configWithNamespace, ctx) shouldBe None
    }
  }
}
