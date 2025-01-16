package pl.touk.nussknacker.engine.api.namespaces

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class NamingStrategySpec extends AnyFunSuite with Matchers {

  test("should leave original names if no namespace configured") {
    val defaultNaming = NamingStrategy(None)
    defaultNaming.prepareName("original") shouldBe "original"
    defaultNaming.decodeName("original") shouldBe Some("original")
  }

  test("should add namespace if configured") {
    val namingStrategy = NamingStrategy(Some(Namespace("customer1", "_")))
    namingStrategy.prepareName("original") shouldBe "customer1_original"
    namingStrategy.decodeName("customer1_someName") shouldBe Some("someName")
    namingStrategy.decodeName("dummy??") shouldBe None
  }

  test("should parse naming strategy from config with default separator") {}

  test("should parse naming strategy from config with separator") {}

}
