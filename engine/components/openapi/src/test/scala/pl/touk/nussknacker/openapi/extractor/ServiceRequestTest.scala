package pl.touk.nussknacker.openapi.extractor

import java.lang
import java.net.URL
import java.util.Collections.singletonMap

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.openapi.extractor.ServiceRequest.SwaggerRequestType
import pl.touk.nussknacker.openapi.parser.SwaggerParser
import sttp.client.StringBody
import sttp.model.Uri

import scala.collection.JavaConverters.{mapAsJavaMapConverter, seqAsJavaListConverter}
import scala.io.Source

class ServiceRequestTest extends FunSuite with Matchers {

  private val baseUrl = new URL("http://baseUrl")

  private val fixedHeaders = List(("Accept-Encoding", "gzip, deflate"))

  private def prepareRequest(location: String, inputParams: List[AnyRef], fixedParams: Map[String, () => AnyRef]): SwaggerRequestType = {
    val rawSwagger =
      Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(location)).mkString
    val swaggerService = SwaggerParser.parse(rawSwagger, Map.empty).head
    val parametersExtractor = new ParametersExtractor(swaggerService, fixedParams)
    ServiceRequest(rootUrl = baseUrl, swaggerService, parametersExtractor.prepareParams(inputParams))
  }

  test("query params to url extraction") {

    val paramInputs: List[AnyRef] = List(
      lang.Long.valueOf(1234L), // pathParam1
      "alamakota", //pathParam1
      List(1, 2).asJava, // queryParam1
      lang.Boolean.valueOf(false), // queryParam2
      Map("NAME" -> "myName").asJava // queryParam3
    )
    val req = prepareRequest("swagger/enricher-with-query-params.yml", paramInputs, Map())

    req.uri.querySegments.toList.map {
      case Uri.QuerySegment.KeyValue(key, value, _, _) => (key, value)
      case _ => ???
    } shouldBe List(
      ("queryParam1", "1"),
      ("queryParam1", "2"),
      ("queryParam2", "false"),
      ("queryParam3.NAME", "myName")
    )
  }

  test("path params to url extraction") {

    val paramInputs: List[AnyRef] = List(
      lang.Long.valueOf(1234L), // pathParam1
      "alamakota", //pathParam1
      List(1, 2).asJava, // queryParam1
      lang.Boolean.valueOf(false), // queryParam2
      Map("NAME" -> "myName").asJava // queryParam3
    )
    val req = prepareRequest("swagger/enricher-with-query-params.yml", paramInputs, Map())

    req.uri.path.mkString("/") shouldBe "someService/someSubPath/1234/otherSubPath/alamakota"
  }

  test("body parameters extraction") {
    val paramInputs: List[AnyRef] = List(
      185: java.lang.Integer, //param1
      List(singletonMap("accountId", 123), singletonMap("accountId", 44)).asJava, // offers
      "terefere" // otherField
    )
    val req = prepareRequest("swagger/enricher-body-param.yml", paramInputs, Map())

    req.uri.toString() shouldBe "http://baseUrl/someService/185"
    req.body.asInstanceOf[StringBody].s shouldBe "{\"offers\":[{\"accountId\":123},{\"accountId\":44}],\"otherField\":\"terefere\"}"
  }

  test("fixed parameters extraction") {
    val fixedParams = Map("System-Name" -> (() => "fixed"), "X-Correlation-ID" -> (() => "54321"))
    val paramInputs: List[AnyRef] = List(
      1234L: java.lang.Long, // accountId
      "User1", // System-User-Id
      "true" //pretty
    )
    val req = prepareRequest("swagger/swagger-20.json", paramInputs, fixedParams)

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
    val paramInputs: List[AnyRef] = List(
      1234L: java.lang.Long, // accountId
      "User1", // System-User-Id
      null //pretty
    )
    val req = prepareRequest("swagger/swagger-20.json", paramInputs, fixedParams)

    req.headers.map { param =>
      (param.name, param.value)
    } shouldBe fixedHeaders ++ List(
      ("System-Name", "fixed"),
      ("System-User-Name", "User1")
    )
    req.uri.toString() shouldBe "http://baseUrl/line/getCollectionData/1234"
  }


}
