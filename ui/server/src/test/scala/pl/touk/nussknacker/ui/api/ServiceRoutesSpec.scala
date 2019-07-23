package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import argonaut.{DecodeJson, DecodeResult, Json}
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.DisplayableAsJson
import pl.touk.nussknacker.engine.management.FlinkProcessManagerProvider
import pl.touk.nussknacker.ui.api.ServiceRoutes.JsonThrowable
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import argonaut._
import Argonaut._
import ArgonautShapeless._
import pl.touk.http.argonaut.{Argonaut62Support, JacksonJsonMarshaller, JsonMarshaller}
import pl.touk.nussknacker.engine.util.service.query.ExpressionServiceQuery.ParametersCompilationException
import pl.touk.nussknacker.engine.util.service.query.ServiceQuery.{QueryResult, ServiceNotFoundException}
import pl.touk.nussknacker.ui.api.helpers.TestPermissions

class ServiceRoutesSpec extends FunSuite with Matchers with ScalatestRouteTest with Argonaut62Support with TestPermissions{

  val category1Deploy = Map("Category1" -> Set(Permission.Deploy))
  implicit val jsonMarshaller: JsonMarshaller = JacksonJsonMarshaller

  private implicit val user = LoggedUser("admin", category1Deploy)
  private val modelData = FlinkProcessManagerProvider.defaultModelData(ConfigFactory.load())
  private val serviceRoutes = new ServiceRoutes(Map(TestProcessingTypes.Streaming -> modelData))


  implicit val queryResultDecoder = DecodeJson[QueryResult] { c =>
    for {
      result <- (c --\ "result").as[String]
    } yield QueryResult(result, List.empty)
  }

  test("invoke service") {
    val entity = HttpEntity(MediaTypes.`application/json`,
      """
        |[
        | {
        |    "name": "param",
        |    "expression": {
        |       "language":"spel",
        |       "expression":"'parameterValue'"
        |    }
        | },
        | {
        |    "name": "tariffType",
        |    "expression": {
        |       "language": "spel",
        |       "expression": "null"
        |    }
        | }
        |]
      """.stripMargin)
    Post("/service/streaming/enricher", entity) ~> serviceRoutes.route ~> check {
      status shouldEqual StatusCodes.OK
      val result = entityAs[QueryResult]
      result.result shouldEqual "RichObject(parameterValue,123,Some(rrrr))" //TODO: should be JSON
    }
  }
  test("display valuable error message for invalid spell expression") {
    val entity = HttpEntity(MediaTypes.`application/json`,
      """
        |[
        | {
        |    "name": "param",
        |    "expression": {
        |       "language":"spel",
        |       "expression":"not valid spell expression"
        |    }
        | }
        |]
      """.stripMargin)
    Post("/service/streaming/enricher", entity) ~> serviceRoutes.route ~> check {
      status shouldEqual StatusCodes.InternalServerError
      entityAs[JsonThrowable].message shouldEqual Some("NonEmptyList(ExpressionParseError(EL1041E: After parsing a valid expression, there is still more data in the expression: 'spell',defaultNodeId,Some(param),not valid spell expression))")
      entityAs[JsonThrowable].className shouldEqual classOf[ParametersCompilationException].getCanonicalName
    }
  }
  test("display valuable error message for mismatching parameters") {
    val entity = HttpEntity(MediaTypes.`application/json`, "[]")
    Post("/service/streaming/enricher", entity) ~> serviceRoutes.route ~> check {
      status shouldEqual StatusCodes.InternalServerError
      entityAs[JsonThrowable].message shouldEqual Some( "Missing parameter: param")
      entityAs[JsonThrowable].className shouldEqual classOf[IllegalArgumentException].getCanonicalName
    }
  }
  test("display valuable error message for missing service") {
    val entity = HttpEntity(MediaTypes.`application/json`, "[]")
    Post("/service/streaming/unexcitingService", entity) ~> serviceRoutes.route ~> check {
      status shouldEqual StatusCodes.NotFound
      entityAs[JsonThrowable].message shouldEqual Some("service unexcitingService not found")
      entityAs[JsonThrowable].className shouldEqual classOf[ServiceNotFoundException].getCanonicalName
    }
  }
  test("prevent unauthorized user service invocation") {
    val user = LoggedUser("nonAdmin")
    serviceRoutes.canUserInvokeService(user, "enricher", modelData) shouldBe false
  }
  test("user with category invoke service") {
    val user = LoggedUser("nonAdmin", category1Deploy)
    serviceRoutes.canUserInvokeService(user, "enricher", modelData) shouldBe true
  }
  test("canUserInvokeService always pass unexciting service") {
    val user = LoggedUser("nonAdmin", category1Deploy)
    serviceRoutes.canUserInvokeService(user, "unexcitingService", modelData) shouldBe true
  }

}

object ServiceRoutesSpec {

  import argonaut._, Argonaut._, ArgonautShapeless._

  case class Response(age: Int, name: String) extends DisplayableAsJson[Response]

}
