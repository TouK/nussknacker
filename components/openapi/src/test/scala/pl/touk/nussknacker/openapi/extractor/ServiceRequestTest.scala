package pl.touk.nussknacker.openapi.extractor

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SingleBodyParameter}
import pl.touk.nussknacker.openapi.extractor.ServiceRequest.SwaggerRequestType
import pl.touk.nussknacker.openapi.parser.SwaggerParser
import sttp.client3.StringBody
import sttp.model.Uri

import java.lang
import java.net.URL
import java.util.Collections.singletonMap
import scala.jdk.CollectionConverters._

class ServiceRequestTest extends AnyFunSuite with Matchers {

  private val baseUrl = new URL("http://baseUrl")

  private val fixedHeaders = List(("Accept-Encoding", "gzip, deflate"))

  private def prepareRequest(
      location: String,
      inputParams: Map[String, Any],
      fixedParams: Map[String, () => AnyRef]
  ): SwaggerRequestType = {
    val openApiConfig       = OpenAPIServicesConfig(baseUrl, allowedMethods = List("POST", "GET"))
    val swaggerService      = SwaggerParser.loadFromResource(location, openApiConfig).flatMap(_.toOption).head
    val parametersExtractor = new ParametersExtractor(swaggerService, fixedParams)
    ServiceRequest(rootUrl = baseUrl, swaggerService, parametersExtractor.prepareParams(inputParams))
  }

  test("query params to url extraction") {

    val paramInputs: Map[String, AnyRef] = Map(
      "pathParam1"  -> lang.Long.valueOf(1234L),
      "pathParam2"  -> "alamakota",
      "queryParam1" -> List(1, 2).asJava,
      "queryParam2" -> lang.Boolean.valueOf(false),
      "queryParam3" -> Map("NAME" -> "myName").asJava
    )
    val req = prepareRequest("/swagger/enricher-with-query-params.yml", paramInputs, Map())

    req.uri.querySegments.toList.map {
      case Uri.QuerySegment.KeyValue(key, value, _, _) => (key, value)
      case _                                           => ???
    } shouldBe List(
      ("queryParam1", "1"),
      ("queryParam1", "2"),
      ("queryParam2", "false"),
      ("queryParam3.NAME", "myName")
    )
  }

  test("path params to url extraction") {

    val paramInputs: Map[String, AnyRef] = Map(
      "pathParam1"  -> lang.Long.valueOf(1234L),
      "pathParam2"  -> "alamakota",
      "queryParam1" -> List(1, 2).asJava,
      "queryParam2" -> lang.Boolean.valueOf(false),
      "queryParam3" -> Map("NAME" -> "myName").asJava
    )

    val req = prepareRequest("/swagger/enricher-with-query-params.yml", paramInputs, Map())

    req.uri.path.mkString("/") shouldBe "someService/someSubPath/1234/otherSubPath/alamakota"
  }

  test("body parameters extraction") {

    val paramInputs: Map[String, Any] = Map(
      "param1"     -> 185,
      "offers"     -> List(singletonMap("accountId", 123), singletonMap("accountId", 44)).asJava,
      "otherField" -> "terefere"
    )
    val req = prepareRequest("/swagger/enricher-body-param.yml", paramInputs, Map())

    req.uri.toString() shouldBe "http://baseUrl/someService/185"
    req.body
      .asInstanceOf[StringBody]
      .s shouldBe "{\"offers\":[{\"accountId\":123},{\"accountId\":44}],\"otherField\":\"terefere\"}"
  }

  test("primitive body extraction") {

    val paramInputs: Map[String, Any] = Map(
      "param1"                 -> 185,
      SingleBodyParameter.name -> "123"
    )
    val req = prepareRequest("/swagger/enricher-primitive-body-param.yml", paramInputs, Map())

    req.uri.toString() shouldBe "http://baseUrl/someService/185"
    req.body.asInstanceOf[StringBody].s shouldBe """"123""""
  }

  test("fixed parameters extraction") {
    val fixedParams = Map("System-Name" -> (() => "fixed"), "X-Correlation-ID" -> (() => "54321"))
    val paramInputs: Map[String, Any] = Map(
      "accountNo"        -> 1234L,
      "System-User-Name" -> "User1",
      "pretty"           -> "true"
    )
    val req = prepareRequest("/swagger/swagger-20.json", paramInputs, fixedParams)

    req.headers.toList.map { header =>
      (header.name, header.value)
    } shouldBe fixedHeaders ++ List(
      ("System-Name", "fixed"),
      ("System-User-Name", "User1"),
      ("X-Correlation-ID", "54321")
    )
    req.uri.toString() shouldBe "http://baseUrl/line/getCollectionData/1234?pretty=true"
  }

  test("Null params handling") {
    val fixedParams = Map("System-Name" -> (() => "fixed"), "X-Correlation-ID" -> (() => null))
    val paramInputs: Map[String, Any] = Map(
      "accountNo"        -> 1234L,
      "System-User-Name" -> "User1",
      "pretty"           -> null
    )
    val req = prepareRequest("/swagger/swagger-20.json", paramInputs, fixedParams)

    req.headers.map { param =>
      (param.name, param.value)
    } shouldBe fixedHeaders ++ List(
      ("System-Name", "fixed"),
      ("System-User-Name", "User1")
    )
    req.uri.toString() shouldBe "http://baseUrl/line/getCollectionData/1234"
  }

}
