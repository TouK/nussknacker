package pl.touk.nussknacker.openapi

import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class OpenAPIsConfigTest extends AnyFunSuite with Matchers with OptionValues {

  import net.ceedubs.ficus.Ficus._
  import OpenAPIsConfig._

  test("should parse apikey security") {
    val config = ConfigFactory.parseString("""url: "http://foo"
        |security {
        |  apikeySecuritySchema {
        |    type: "apiKey"
        |    apiKeyValue: "34534asfdasf"
        |  }
        |}""".stripMargin)

    val parsedConfig = config.as[OpenAPIServicesConfig]
    parsedConfig.security.value.get("apikeySecuritySchema").value shouldEqual ApiKeyConfig(apiKeyValue = "34534asfdasf")
  }

}
