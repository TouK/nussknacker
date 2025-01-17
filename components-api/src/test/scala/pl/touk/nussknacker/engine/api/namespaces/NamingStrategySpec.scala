package pl.touk.nussknacker.engine.api.namespaces

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.namespaces.NamespaceContext.{Flink, Kafka}

class NamingStrategySpec extends AnyFunSuite with Matchers {

  test("should leave original names if no namespace configured") {
    val defaultNaming = NamingStrategy.Disabled
    defaultNaming.prepareName("original", Flink) shouldBe "original"
    defaultNaming.decodeName("original", Flink) shouldBe Some("original")
  }

  test("should add namespace if configured") {
    val namingStrategy = NamingStrategy(Some(Namespace("customer1", "_")), Map.empty)
    namingStrategy.prepareName("original", Flink) shouldBe "customer1_original"
    namingStrategy.decodeName("customer1_someName", Flink) shouldBe Some("someName")
    namingStrategy.decodeName("dummy??", Flink) shouldBe None
  }

  test("should use namespace configuration for context if available") {
    val namingStrategy = NamingStrategy(Some(Namespace("customer1", "_")), Map(Kafka -> Namespace("cust1", ".")))
    namingStrategy.prepareName("original", Flink) shouldBe "customer1_original"
    namingStrategy.prepareName("original", Kafka) shouldBe "cust1.original"
    namingStrategy.decodeName("customer1_someName", Flink) shouldBe Some("someName")
    namingStrategy.decodeName("cust1.someName", Kafka) shouldBe Some("someName")
    namingStrategy.decodeName("dummy??", Flink) shouldBe None
    namingStrategy.decodeName("dummy??", Kafka) shouldBe None
  }

  test("should read disabled naming strategy config") {
    val namingStrategy = NamingStrategy.fromConfig(ConfigFactory.empty())
    namingStrategy.prepareName("original", Flink) shouldBe "original"
  }

  test("should read naming strategy config with default separator") {
    val config         = ConfigFactory.parseString("""namespace: customer1""")
    val namingStrategy = NamingStrategy.fromConfig(config)
    namingStrategy.prepareName("original", Flink) shouldBe "customer1_original"
  }

  test("should read naming strategy config with specified separator") {
    val config = ConfigFactory.parseString("""
        |namespace: customer1
        |namespaceSeparator: "."""".stripMargin)
    val namingStrategy = NamingStrategy.fromConfig(config)
    namingStrategy.prepareName("original", Flink) shouldBe "customer1.original"
  }

  test("should read naming strategy config object without overrides") {
    val config = ConfigFactory.parseString("""
        |namespace: {
        |  value: customer1
        |  separator: "."
        |}""".stripMargin)
    val namingStrategy = NamingStrategy.fromConfig(config)
    namingStrategy.prepareName("original", Flink) shouldBe "customer1.original"
  }

  test("should read naming strategy config object with overrides") {
    val config = ConfigFactory.parseString("""
        |namespace: {
        |  value: customer1
        |  overrides: {
        |    kafka: {
        |      value: customer1_internal
        |      separator: "."
        |    }
        |  }
        |}""".stripMargin)
    val namingStrategy = NamingStrategy.fromConfig(config)
    namingStrategy.prepareName("original", Flink) shouldBe "customer1_original"
    namingStrategy.prepareName("original", Kafka) shouldBe "customer1_internal.original"
  }

}
