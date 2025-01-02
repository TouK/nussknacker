package pl.touk.nussknacker.openapi.parser

import cats.data.Validated.Valid
import org.scalatest.Inside.inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.json.swagger
import pl.touk.nussknacker.engine.json.swagger._
import pl.touk.nussknacker.openapi._

import scala.collection.immutable.ListMap

class SwaggerParserTest extends AnyFunSuite with BaseOpenAPITest with Matchers {

  test("reads swagger 2.0") {

    val openApi = parseServicesFromResourceUnsafe("swagger-20.json").head

    openApi.name.value shouldBe "getCollectionDataUsingGET"

    openApi.parameters shouldBe List(
      UriParameter("accountNo", SwaggerLong),
      QueryParameter("pretty", SwaggerBool),
      HeaderParameter("System-Name", SwaggerString),
      HeaderParameter("System-User-Name", SwaggerString),
      HeaderParameter("X-Correlation-ID", SwaggerString),
    )

    openApi.pathParts shouldBe List(PlainPart("line"), PlainPart("getCollectionData"), PathParameterPart("accountNo"))

  }

  test("reads body params") {

    val openApi =
      parseServicesFromResourceUnsafe("enricher-body-param.yml", baseConfig.copy(allowedMethods = List("POST"))).head

    openApi.name.value shouldBe "testService"
    openApi.parameters shouldBe List(
      UriParameter("param1", SwaggerLong),
      SingleBodyParameter(
        SwaggerObject(
          Map(
            "offers"     -> SwaggerArray(swagger.SwaggerObject(Map("accountId" -> SwaggerLong))),
            "otherField" -> SwaggerString
          )
        )
      )
    )

    openApi.pathParts shouldBe List(PlainPart("someService"), PathParameterPart("param1"))
  }

  test("reads primitive body") {

    val openApi = parseServicesFromResourceUnsafe(
      "enricher-primitive-body-param.yml",
      baseConfig.copy(allowedMethods = List("POST"))
    ).head

    openApi.name.value shouldBe "testService"
    openApi.parameters shouldBe List(
      UriParameter("param1", SwaggerLong),
      SingleBodyParameter(SwaggerString)
    )

    openApi.pathParts shouldBe List(PlainPart("someService"), PathParameterPart("param1"))
  }

  test("reads only configured methods and patterns") {

    forAll(
      Table(
        ("allowedMethods", "namePattern", "expectedNames"),
        (List("GET", "POST"), ".*", List("getService", "postService")),
        (List("GET"), ".*", List("getService")),
        (List("GET", "POST"), "post.*", List("postService")),
        (List("GET"), "post.*", List())
      )
    ) { (allowedMethods, namePattern, expectedNames) =>
      parseServicesFromResourceUnsafe(
        "multiple-operations.yml",
        baseConfig.copy(allowedMethods = allowedMethods, namePattern = namePattern.r)
      ).map(_.name.value) shouldBe expectedNames
    }

  }

  test("detects documentation") {
    val openApi =
      parseServicesFromResourceUnsafe("multiple-operations.yml", baseConfig.copy(allowedMethods = List("GET", "POST")))

    openApi.find(_.name.value == "getService").flatMap(_.documentation) shouldBe Some("https://nussknacker.io")
    openApi.find(_.name.value == "postService").flatMap(_.documentation) shouldBe Some("https://touk.pl")

  }

  test("returns errors for incorrect service") {
    val openApi = parseServicesFromResource("incorrect-service.yml")

    openApi.find(_.exists(_.name == ServiceName("GET-valid"))) shouldBe Symbol("defined")

    def errorsFor(name: String) =
      openApi.flatMap(_.swap.toOption).filter(_.name == ServiceName("GET-" + name)).flatMap(_.errors.toList)

    errorsFor("noResponseType") shouldBe List("No response with application/json or */* media types found")
    errorsFor("unhandledSecurity") shouldBe List(
      "No security requirement can be met because: there is no security secret configured for scheme name \"headerConfig\""
    )
    errorsFor("unhandledFormat") shouldBe List("Type 'number' in format 'decimal' is not supported")

  }

  test("handles recursive scheme") {
    val openApi = parseServicesFromResourceUnsafe("recursive.yml")

    val responseType = openApi.find(_.name == ServiceName("testRecursive")).flatMap(_.responseSwaggerType)
    val recursiveListType = SwaggerObject(
      Map(
        "value" -> SwaggerString,
        "next"  -> SwaggerAny,
        "union" -> SwaggerUnion(List(SwaggerString, SwaggerAny)),
        "list"  -> SwaggerArray(SwaggerAny)
      )
    )
    responseType shouldBe Some(
      SwaggerObject(
        Map(
          "left"  -> recursiveListType,
          "right" -> recursiveListType
        )
      )
    )
    recursiveListType.typingResult shouldBe Typed.record(
      ListMap(
        "value" -> Typed[String],
        "next"  -> Unknown,
        // union String + Unknown
        "union" -> Unknown,
        "list"  -> Typed.genericTypeClass[java.util.List[_]](List(Unknown))
      )
    )

  }

  test("should handle array return type with 3.1") {
    val openApi = parseServicesFromResource("swagger-3.1-array.yml")
    inside(openApi) { case Valid(service) :: Nil =>
      service.responseSwaggerType shouldBe Some(
        SwaggerObject(
          Map(
            "itemsWithType" -> SwaggerArray(SwaggerString),
          )
        )
      )
    }
  }

  test("handles local references in swagger 2.0") {
    val openApi = parseServicesFromResource("swagger-2.0-refs.yml")
    val openApiService = openApi.headOption match {
      case Some(Valid(openApiService)) => openApiService
      case _                           => fail("Failed to parse Swagger service")
    }
    openApiService.responseSwaggerType shouldBe Some(
      SwaggerObject(
        Map(
          "message" -> SwaggerString,
        )
      )
    )
    openApiService.parameters shouldBe List(
      QueryParameter("queryParam", SwaggerString)
    )
  }

}
