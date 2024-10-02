package pl.touk.nussknacker.http

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.http.enricher.HttpEnricher.HttpMethod.{DELETE, GET, POST, PUT}
import pl.touk.nussknacker.http.enricher.HttpEnricherFactory

class HttpEnricherConfigTest extends AnyFunSuite with Matchers {

  test("should create enricher with defaults for empty config") {
    new HttpEnricherComponentProvider()
      .create(ConfigFactory.empty(), ProcessObjectDependencies.withConfig(ConfigFactory.empty()))
      .head
      .component
      .asInstanceOf[HttpEnricherFactory]
      .config should matchPattern { case HttpEnricherConfig(None, None, _, List(GET, POST, PUT, DELETE)) =>
    }
  }

  test("should throw exception when creating enricher with root url with query parameters") {
    val config = ConfigFactory.parseString(s"""
         |{
         |  rootUrl: "http://example.io?someIntParam=123"
         |}
         |""".stripMargin)
    intercept[IllegalArgumentException] {
      new HttpEnricherComponentProvider()
        .create(config, ProcessObjectDependencies.withConfig(config))
    }.getMessage shouldBe "Root URL for HTTP enricher has to be without query parameters."
  }

  test("should throw exception when creating enricher with empty allowed methods") {
    val config = ConfigFactory.parseString(s"""
                                              |{
                                              |  allowedMethods: []
                                              |}
                                              |""".stripMargin)
    intercept[IllegalArgumentException] {
      new HttpEnricherComponentProvider()
        .create(config, ProcessObjectDependencies.withConfig(config))
    }.getMessage shouldBe "Allowed methods cannot be empty."
  }

}
