package pl.touk.nussknacker.engine.api.namespaces

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class NamingStrategySpec extends AnyFunSuite with Matchers {

  test("should leave original names if no namespace configured") {
    val defaultNaming = NamingStrategy.Disabled

    defaultNaming.prepareName("original") shouldBe "original"
    defaultNaming.decodeName("original") shouldBe Some("original")
  }

  test("should add namespace if configured") {
    val namingStrategy = NamingStrategy(Some(Namespace("customer1", "_")))

    namingStrategy.prepareName("original") shouldBe "customer1_original"
    namingStrategy.decodeName("customer1_someName") shouldBe Some("someName")
    namingStrategy.decodeName("dummy??") shouldBe None
  }

  test("should read disabled naming strategy config") {
    val namingStrategy = NamingStrategy.fromConfig(ConfigFactory.empty())

    namingStrategy.prepareName("original") shouldBe "original"
  }

  test("should read naming strategy config with default separator") {
    val config = ConfigFactory.parseString("""namespace: customer1""")

    val namingStrategy = NamingStrategy.fromConfig(config)

    namingStrategy.prepareName("original") shouldBe "customer1_original"
  }

  test("should read naming strategy config with specified separator") {
    val config = ConfigFactory.parseString("""
        |namespace: customer1
        |namespaceSeparator: "."""".stripMargin)

    val namingStrategy = NamingStrategy.fromConfig(config)

    namingStrategy.prepareName("original") shouldBe "customer1.original"
  }

}
