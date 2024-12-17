package pl.touk.nussknacker.openapi

import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

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

}
