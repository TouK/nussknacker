package pl.touk.nussknacker.openapi.parser

import java.net.URL
import java.nio.charset.Charset

import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.openapi.{HeaderParameter, _}
import org.scalatest.{FunSuite, Matchers}

class SwaggerParserTest extends FunSuite with Matchers {

  test("reads swagger 2.0") {

    val swaggerJson = IOUtils.toString(getClass.getResourceAsStream("/swagger/swagger-20.json"), "UTF-8")

    val openApi = SwaggerParser.parse(swaggerJson, Map.empty).head

    openApi.name shouldBe "getCollectionDataUsingGET"

    openApi.parameters shouldBe List(
      UriParameter("accountNo", SwaggerLong),
      HeaderParameter("System-Name", SwaggerString),
      HeaderParameter("System-User-Name", SwaggerString),
      HeaderParameter("X-Correlation-ID", SwaggerString),
      QueryParameter("pretty", SwaggerBool)
    )

    openApi.pathParts shouldBe List(PlainPart("line"), PlainPart("getCollectionData"), PathParameterPart("accountNo"))

  }

  test("reads body params") {
    val swaggerJson = IOUtils.toString(getClass.getResourceAsStream("/swagger/enricher-body-param.yml"), null.asInstanceOf[Charset])

    val openApi = SwaggerParser.parse(swaggerJson, Map.empty).head

    openApi.name shouldBe "testService"

    openApi.parameters shouldBe List(
      UriParameter("param1", SwaggerLong),
      SingleBodyParameter(SwaggerObject(Map(
        "offers" -> SwaggerArray(SwaggerObject(Map("accountId" -> SwaggerLong), Set[String]())),
        "otherField" -> SwaggerString
      ), Set[String]()))
    )

    openApi.pathParts shouldBe List(PlainPart("someService"), PathParameterPart("param1"))

  }

}
