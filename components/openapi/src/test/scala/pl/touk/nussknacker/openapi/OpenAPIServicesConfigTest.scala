package pl.touk.nussknacker.openapi

import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.http.backend.{DefaultHttpClientConfig, HttpClientConfig}

import scala.concurrent.duration.DurationInt

class OpenAPIServicesConfigTest extends AnyFunSuite with Matchers with OptionValues {

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

    val parsedConfig = OpenAPIServicesConfig.parse(config)
    parsedConfig.securityConfig
      .apiKeySecret(SecuritySchemeName("apikeySecurityScheme"))
      .value shouldEqual ApiKeySecret(apiKeyValue = "34534asfdasf")
    parsedConfig.securityConfig
      .apiKeySecret(SecuritySchemeName("apikeySecurityScheme2"))
      .value shouldEqual ApiKeySecret(apiKeyValue = "123")
  }

  test("should parse common apikey secret for any scheme with single secret field") {
    val config = ConfigFactory.parseString("""url: "http://foo"
                                             |secret {
                                             |  type: "apiKey"
                                             |  apiKeyValue: "34534asfdasf"
                                             |}""".stripMargin)

    val parsedConfig = OpenAPIServicesConfig.parse(config)
    parsedConfig.securityConfig
      .apiKeySecret(SecuritySchemeName("someScheme"))
      .value shouldEqual ApiKeySecret(apiKeyValue = "34534asfdasf")
    parsedConfig.securityConfig
      .apiKeySecret(SecuritySchemeName("someOtherScheme"))
      .value shouldEqual ApiKeySecret(apiKeyValue = "34534asfdasf")
  }

  test("should parse oauth2 client credentials secret for each scheme") {
    val config = ConfigFactory.parseString("""url: "http://foo"
                                             |security {
                                             |  oauth2SecurityScheme {
                                             |    type: "oauth2ClientCredentials"
                                             |    clientId: "my-client-id"
                                             |    clientSecret: "my-client-secret"
                                             |  }
                                             |}""".stripMargin)

    val parsedConfig = OpenAPIServicesConfig.parse(config)
    parsedConfig.securityConfig
      .oauth2ClientCredentialsSecret(SecuritySchemeName("oauth2SecurityScheme"))
      .value shouldEqual OAuth2ClientCredentialsSecret("my-client-id", "my-client-secret")
  }

  test("should parse common oauth2 client credentials secret for any scheme") {
    val config = ConfigFactory.parseString("""url: "http://foo"
                                             |secret {
                                             |  type: "oauth2ClientCredentials"
                                             |  clientId: "my-client-id"
                                             |  clientSecret: "my-client-secret"
                                             |}""".stripMargin)

    val parsedConfig = OpenAPIServicesConfig.parse(config)
    parsedConfig.securityConfig
      .oauth2ClientCredentialsSecret(SecuritySchemeName("someScheme"))
      .value shouldEqual OAuth2ClientCredentialsSecret("my-client-id", "my-client-secret")
    parsedConfig.securityConfig
      .oauth2ClientCredentialsSecret(SecuritySchemeName("someOtherScheme"))
      .value shouldEqual OAuth2ClientCredentialsSecret("my-client-id", "my-client-secret")
  }

  test("should parse common secrets") {
    val config = ConfigFactory.parseString("""url: "http://foo"
                                             |secrets: [
                                             |  {
                                             |    type: "apiKey"
                                             |    apiKeyValue: "34534asfdasf"
                                             |  },
                                             |  {
                                             |    type: "basicAuth"
                                             |    username: "u1"
                                             |    password: "p1"
                                             |  }
                                             |]""".stripMargin)

    val parsedConfig = OpenAPIServicesConfig.parse(config)
    parsedConfig.securityConfig
      .apiKeySecret(SecuritySchemeName("someScheme"))
      .value shouldEqual ApiKeySecret(apiKeyValue = "34534asfdasf")
    parsedConfig.securityConfig
      .httpBasicAuthSecret(SecuritySchemeName("someOtherScheme"))
      .value shouldEqual HttpBasicAuthSecret(username = "u1", password = "p1")
  }

  test("should not allow secret and secrets config keys at the same time") {
    val config = ConfigFactory.parseString("""url: "http://foo"
                                             |secret: {
                                             |  type: "apiKey"
                                             |  apiKeyValue: "34534asfdasf"
                                             |},
                                             |secrets: [
                                             |  {
                                             |    type: "apiKey"
                                             |    apiKeyValue: "34534asfdasf"
                                             |  }
                                             |]""".stripMargin)
    intercept[RuntimeException] {
      OpenAPIServicesConfig.parse(config)
    }.getMessage shouldBe "Both 'secrets' and 'secret' fields cannot be configured at the same time"
  }

  test("should not allow multiple common secrets of same type") {
    val config = ConfigFactory.parseString("""url: "http://foo"
                                             |secrets: [
                                             |  {
                                             |    type: "apiKey"
                                             |    apiKeyValue: "34534asfdasf"
                                             |  },
                                             |  {
                                             |    type: "apiKey"
                                             |    apiKeyValue: "34534asfdasf"
                                             |  }
                                             |]""".stripMargin)
    intercept[RuntimeException] {
      OpenAPIServicesConfig.parse(config)
    }.getMessage shouldBe "requirement failed: Only one `apiKey` common secret (with unspecified scheme name) is allowed"
  }

  test("should not allow multiple common oauth2 client credentials secrets") {
    val config = ConfigFactory.parseString("""url: "http://foo"
                                             |secrets: [
                                             |  {
                                             |    type: "oauth2ClientCredentials"
                                             |    clientId: "my-client-id"
                                             |    clientSecret: "my-client-secret"
                                             |  },
                                             |  {
                                             |    type: "oauth2ClientCredentials"
                                             |    clientId: "my-client-id-2"
                                             |    clientSecret: "my-client-secret-2"
                                             |  }
                                             |]""".stripMargin)
    intercept[RuntimeException] {
      OpenAPIServicesConfig.parse(config)
    }.getMessage shouldBe
      "requirement failed: Only one `oauth2ClientCredentials` common secret (with unspecified scheme name) is allowed"
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

    val parsedConfig = OpenAPIServicesConfig.parse(config)
    parsedConfig.securityConfig
      .apiKeySecret(SecuritySchemeName("someScheme"))
      .value shouldEqual ApiKeySecret(apiKeyValue = "123")
    parsedConfig.securityConfig
      .apiKeySecret(SecuritySchemeName("someOtherScheme"))
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
                                             |  forbiddenCidrs: ["127.0.0.0/8", "::1/128"]
                                             |}
                                             |  """.stripMargin)

    val parsedConfig = OpenAPIServicesConfig.parse(config)
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
      forbiddenCidrs = Some(List("127.0.0.0/8", "::1/128")),
    )
  }

  test("should parse definition cache settings") {
    val config = ConfigFactory.parseString("""url: "http://foo"
                                             |openApiServicesDiscoveryCacheTtl: "5 minutes"
                                             |cacheFileLocation: "/cache/openapi-services.json"
                                             |""".stripMargin)

    val parsedConfig = OpenAPIServicesConfig.parse(config)
    parsedConfig.openApiServicesDiscoveryCacheTtl shouldBe 5.minutes
    parsedConfig.cacheFileLocation shouldBe Some("/cache/openapi-services.json")
  }

}
