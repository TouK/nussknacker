package pl.touk.nussknacker.openapi.parser

import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.openapi.{HeaderParameter, _}

import java.net.URL

class SwaggerParserTest extends FunSuite with BaseOpenAPITest with Matchers {

  test("reads swagger 2.0") {

    val openApi = parseServicesFromResource("swagger-20.json").head

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

    val openApi = parseServicesFromResource("enricher-body-param.yml", baseConfig.copy(allowedMethods = List("POST"))).head

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

    forAll(Table(("allowedMethods", "namePattern", "expectedNames"),
      (List("GET", "POST"), ".*", List("getService", "postService")),
      (List("GET"), ".*", List("getService")),
      (List("GET", "POST"), "post.*", List("postService")),
      (List("GET"), "post.*", List())
    )) { (allowedMethods, namePattern, expectedNames) =>
      parseServicesFromResource("multiple-operations.yml", baseConfig.copy(allowedMethods = allowedMethods,
        namePattern = namePattern.r)).map(_.name) shouldBe expectedNames
    }

  }

  test("reads simple embedded schema") {
    val openApi = parseServicesFromResource("embedded-schema.json", baseConfig).head
    openApi.responseSwaggerType shouldBe Some(SwaggerObject(Map(
        "id" -> SwaggerLong,
        "coord" -> SwaggerObject(Map("lon" -> SwaggerBigDecimal, "lat" -> SwaggerBigDecimal), Set[String]())
      ), Set[String]()))
  }

}
