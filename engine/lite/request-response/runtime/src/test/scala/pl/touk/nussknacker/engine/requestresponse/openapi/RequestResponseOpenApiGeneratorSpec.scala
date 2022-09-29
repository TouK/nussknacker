package pl.touk.nussknacker.engine.requestresponse.openapi

import io.circe.Json
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.requestresponse.OpenApiDefinitionConfig.defaultOpenApiVersion

class RequestResponseOpenApiGeneratorSpec extends AnyFunSuite with Matchers with OptionValues {

  private val openApiInfo = OApiInfo("fooTitle", version = "fooVersion")

  private val generator = new RequestResponseOpenApiGenerator(defaultOpenApiVersion, openApiInfo)

  private val stubPathDefinitionGenerator = new PathOpenApiDefinitionGenerator {
    override def generatePathOpenApiDefinitionPart(): Option[Json] = Some(Json.obj())
  }

  test("generate definition using default server url when no server description provided") {
    val defaultServerUrl = "/foo"
    val definition = generator.generateOpenApiDefinition(stubPathDefinitionGenerator, serverDescriptions = List.empty, defaultServerUrl)
    val servers = definition.hcursor.downField("servers").focus.value
    val serversArray = servers.asArray.value
    serversArray should have size 1
    servers.hcursor.downN(0).downField("url").focus.value.asString.value shouldEqual defaultServerUrl
  }

  test("generate definition using server urls from config instead of default one") {
    val defaultServerUrl = "/foo"
    val urlFromConfig = "/bar"
    val urlFromConfig2 = "/baz"
    val definition = generator.generateOpenApiDefinition(stubPathDefinitionGenerator, serverDescriptions = List(OApiServer(urlFromConfig, None), OApiServer(urlFromConfig2, None)), defaultServerUrl)
    val servers = definition.hcursor.downField("servers").focus.value
    val serversArray = servers.asArray.value
    serversArray should have size 2
    servers.hcursor.downN(0).downField("url").focus.value.asString.value shouldEqual urlFromConfig
    servers.hcursor.downN(1).downField("url").focus.value.asString.value shouldEqual urlFromConfig2
  }

}
