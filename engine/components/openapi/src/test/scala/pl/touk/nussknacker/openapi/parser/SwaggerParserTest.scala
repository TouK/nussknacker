package pl.touk.nussknacker.openapi.parser

import org.apache.commons.io.IOUtils
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.openapi.{HeaderParameter, _}

class SwaggerParserTest extends FunSuite with Matchers {

  private val baseConfig = OpenAPIServicesConfig()

  test("reads swagger 2.0") {

    val swaggerJson = IOUtils.toString(getClass.getResourceAsStream("/swagger/swagger-20.json"), "UTF-8")

    val openApi = SwaggerParser.parse(swaggerJson, baseConfig).head

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
    val swaggerJson = IOUtils.toString(getClass.getResourceAsStream("/swagger/enricher-body-param.yml"))

    val openApi = SwaggerParser.parse(swaggerJson, baseConfig.copy(allowedMethods = List("POST"))).head

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

  test("reads only configured methods and patterns") {

    val swaggerJson = IOUtils.toString(getClass.getResourceAsStream("/swagger/multiple-operations.yml"))

    forAll(Table(("allowedMethods", "namePattern", "expectedNames"),
      (List("GET", "POST"), ".*", List("getService", "postService")),
      (List("GET"), ".*", List("getService")),
      (List("GET", "POST"), "post.*", List("postService")),
      (List("GET"), "post.*", List())
    )) { (allowedMethods, namePattern, expectedNames) =>
      SwaggerParser.parse(swaggerJson, baseConfig.copy(allowedMethods = allowedMethods,
        namePattern = namePattern.r)).map(_.name) shouldBe expectedNames


    }


  }

}
