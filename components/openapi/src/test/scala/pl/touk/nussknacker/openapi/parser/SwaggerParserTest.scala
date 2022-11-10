package pl.touk.nussknacker.openapi.parser

import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.json.swagger
import pl.touk.nussknacker.engine.json.swagger.{SwaggerArray, SwaggerBool, SwaggerLong, SwaggerObject, SwaggerString}
import pl.touk.nussknacker.openapi._

class SwaggerParserTest extends AnyFunSuite with BaseOpenAPITest with Matchers {

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
        "offers" -> SwaggerArray(swagger.SwaggerObject(Map("accountId" -> SwaggerLong))),
        "otherField" -> SwaggerString
      )))
    )

    openApi.pathParts shouldBe List(PlainPart("someService"), PathParameterPart("param1"))
  }

  test("reads primitive body") {

    val openApi = parseServicesFromResource("enricher-primitive-body-param.yml", baseConfig.copy(allowedMethods = List("POST"))).head

    openApi.name shouldBe "testService"
    openApi.parameters shouldBe List(
      UriParameter("param1", SwaggerLong),
      SingleBodyParameter(SwaggerString)
    )

    openApi.pathParts shouldBe List(PlainPart("someService"), PathParameterPart("param1"))
  }

  test("reads only configured methods and patterns") {

    forAll(Table(
      ("allowedMethods", "namePattern", "expectedNames"),
      (List("GET", "POST"), ".*", List("getService", "postService")),
      (List("GET"), ".*", List("getService")),
      (List("GET", "POST"), "post.*", List("postService")),
      (List("GET"), "post.*", List())
    )) { (allowedMethods, namePattern, expectedNames) =>
      parseServicesFromResource("multiple-operations.yml", baseConfig.copy(allowedMethods = allowedMethods,
        namePattern = namePattern.r)).map(_.name) shouldBe expectedNames
    }

  }

}
