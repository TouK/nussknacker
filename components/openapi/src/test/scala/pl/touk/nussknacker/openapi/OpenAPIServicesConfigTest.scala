package pl.touk.nussknacker.openapi

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.http.backend.{DefaultHttpClientConfig, HttpClientConfig}

import scala.concurrent.duration.DurationInt

class OpenAPIServicesConfigTest extends AnyFunSuite with Matchers with OptionValues {

  import net.ceedubs.ficus.Ficus._

  import OpenAPIServicesConfig._

  test("should parse apikey secret for each scheme") {
    val config = ConfigFactory.parseString("""url: "http://foo"
                                             |security {
                                             |  apikeySecurityScheme {
                                             |    type: "apiKey"
                                             |    apiKeyValue: "34534asfdasf"
                                             |  }
                                             |  apikeySecurityScheme2 {
                                             |    type: "apiKey"
                                             |    apiKeyValue: "123"
                                             |  }
                                             |}""".stripMargin)

    val parsedConfig = config.as[OpenAPIServicesConfig]
    parsedConfig.securityConfig
      .secret(SecuritySchemeName("apikeySecurityScheme"))
      .value shouldEqual ApiKeySecret(apiKeyValue = "34534asfdasf")
    parsedConfig.securityConfig
      .secret(SecuritySchemeName("apikeySecurityScheme2"))
      .value shouldEqual ApiKeySecret(apiKeyValue = "123")
  }

  test("should parse common apikey secret for any scheme") {
    val config = ConfigFactory.parseString("""url: "http://foo"
                                             |secret {
                                             |  type: "apiKey"
                                             |  apiKeyValue: "34534asfdasf"
                                             |}""".stripMargin)

    val parsedConfig = config.as[OpenAPIServicesConfig]
    parsedConfig.securityConfig
      .secret(SecuritySchemeName("someScheme"))
      .value shouldEqual ApiKeySecret(apiKeyValue = "34534asfdasf")
    parsedConfig.securityConfig
      .secret(SecuritySchemeName("someOtherScheme"))
      .value shouldEqual ApiKeySecret(apiKeyValue = "34534asfdasf")
  }

  test("should parse combined apikey secret for each scheme and common apikey secret for any scheme") {
    val config = ConfigFactory.parseString("""url: "http://foo"
                                             |security {
                                             |  someScheme {
                                             |    type: "apiKey"
                                             |    apiKeyValue: "123"
                                             |  }
                                             |}
                                             |secret {
                                             |  type: "apiKey"
                                             |  apiKeyValue: "234"
                                             |}""".stripMargin)

    val parsedConfig = config.as[OpenAPIServicesConfig]
    parsedConfig.securityConfig
      .secret(SecuritySchemeName("someScheme"))
      .value shouldEqual ApiKeySecret(apiKeyValue = "123")
    parsedConfig.securityConfig
      .secret(SecuritySchemeName("someOtherScheme"))
      .value shouldEqual ApiKeySecret(apiKeyValue = "234")
  }

  test("should parse http client config") {
    val config = ConfigFactory.parseString("""url: "http://foo"
                                             |httpClientConfig {
                                             |  timeout: "5 seconds"
                                             |  connectTimeout: "1 seconds"
                                             |  maxPoolSize: 10
                                             |  useNative: true
                                             |  followRedirect: true
                                             |  forceShutdown: true
                                             |  configForProcess: {
                                             |    "someScenario": {
                                             |      timeout: "3 seconds"
                                             |    }
                                             |  }
                                             |  isLocalhostAllowed: false
                                             |  forbiddenHostRegexes: ["namespace.service.svc.cluster.local"]
                                             |}
                                             |  """.stripMargin)

    val parsedConfig = config.as[OpenAPIServicesConfig]

    parsedConfig.httpClientConfig shouldBe HttpClientConfig(
      timeout = Some(5 seconds),
      connectTimeout = Some(1 seconds),
      maxPoolSize = Some(10),
      useNative = Some(true),
      followRedirect = Some(true),
      forceShutdown = Some(true),
      configForProcess = Some(
        Map(
          "someScenario" -> DefaultHttpClientConfig().copy(timeout = Some(3 seconds))
        )
      ),
      forbiddenHostRegexes = Some(List("namespace.service.svc.cluster.local")),
      isLocalhostAllowed = Some(false),
    )
  }

}
