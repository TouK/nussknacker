package pl.touk.nussknacker.openapi.parser

import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.json.swagger
import pl.touk.nussknacker.engine.json.swagger.{SwaggerArray, SwaggerBool, SwaggerLong, SwaggerObject, SwaggerString}
import pl.touk.nussknacker.openapi._

class SwaggerParserTest extends AnyFunSuite with BaseOpenAPITest with Matchers {

  test("reads swagger 2.0") {

    val openApi = parseServicesFromResourceUnsafe("swagger-20.json").head

    openApi.name.value shouldBe "getCollectionDataUsingGET"

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

    val openApi = parseServicesFromResourceUnsafe("enricher-body-param.yml", baseConfig.copy(allowedMethods = List("POST"))).head

    openApi.name.value shouldBe "testService"
    openApi.parameters shouldBe List(
      UriParameter("param1", SwaggerLong),
      SingleBodyParameter(SwaggerObject(Map(
        "offers" -> SwaggerArray(swagger.SwaggerObject(Map("accountId" -> SwaggerLong), Set[String]())),
        "otherField" -> SwaggerString
      ), Set[String]()))
    )

    openApi.pathParts shouldBe List(PlainPart("someService"), PathParameterPart("param1"))
  }

  test("reads primitive body") {

    val openApi = parseServicesFromResourceUnsafe("enricher-primitive-body-param.yml", baseConfig.copy(allowedMethods = List("POST"))).head

    openApi.name.value shouldBe "testService"
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
      parseServicesFromResourceUnsafe("multiple-operations.yml", baseConfig.copy(allowedMethods = allowedMethods,
        namePattern = namePattern.r)).map(_.name.value) shouldBe expectedNames
    }

  }

  test("detects documentation") {
    val openApi = parseServicesFromResourceUnsafe("multiple-operations.yml", baseConfig.copy(allowedMethods = List("GET", "POST")))

    openApi.find(_.name.value == "getService").flatMap(_.documentation) shouldBe Some("https://nussknacker.io")
    openApi.find(_.name.value == "postService").flatMap(_.documentation) shouldBe Some("https://touk.pl")

  }

  test("returns errors for incorrect service") {
    val openApi = parseServicesFromResource("incorrect-service.yml")

    openApi.find(_.exists(_.name == ServiceName("valid"))) shouldBe 'defined
    openApi.find(_.swap.exists(_.name == ServiceName("noResponseType"))) shouldBe 'defined

  }

}
