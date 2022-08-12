package pl.touk.nussknacker.engine.util.namespaces

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.namespaces.{CustomUsageKey, FlinkUsageKey, KafkaUsageKey, NamingContext}

class DefaultNamespacedObjectNamingSpec extends AnyFunSuite with Matchers {

  private val emptyConfig = ConfigFactory.empty()

  private val configWithNamespace = ConfigFactory.empty()
    .withValue(DefaultNamespacedObjectNaming.NamespacePath, fromAnyRef("customer1"))

  private val defaultNaming = DefaultNamespacedObjectNaming

  test("should leave original names if no namespace configured") {
    Array(FlinkUsageKey, KafkaUsageKey, CustomUsageKey("1")).foreach { key =>
      val ctx = new NamingContext(key)

      defaultNaming.prepareName("original", emptyConfig, ctx) shouldBe "original"
      defaultNaming.objectNamingParameters("original", emptyConfig, ctx) shouldBe None
      defaultNaming.decodeName("original", emptyConfig, ctx) shouldBe Some("original")
    }
  }

  test("should add namespace if configured") {
    Array(FlinkUsageKey, KafkaUsageKey, CustomUsageKey("1")).foreach { key =>
      val ctx = new NamingContext(key)

      defaultNaming.prepareName("original", configWithNamespace, ctx) shouldBe "customer1_original"
      defaultNaming.objectNamingParameters("original", configWithNamespace, ctx) shouldBe
        Some(DefaultNamespacedObjectNamingParameters("original", "customer1"))
      defaultNaming.decodeName("customer1_someName", configWithNamespace, ctx) shouldBe Some("someName")
      defaultNaming.decodeName("dummy??", configWithNamespace, ctx) shouldBe None
    }
  }
}
