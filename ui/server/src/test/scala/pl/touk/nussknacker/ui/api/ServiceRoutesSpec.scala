package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.DisplayJsonWithEncoder
import pl.touk.nussknacker.ui.api.ServiceRoutes.JsonThrowable
import pl.touk.nussknacker.ui.api.helpers.{TestPermissions, TestProcessingTypes}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import io.circe.Decoder
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.engine.util.service.query.ServiceQuery.{QueryResult, ServiceInvocationException, ServiceNotFoundException}
import pl.touk.nussknacker.ui.api.helpers.TestFactory.mapProcessingTypeDataProvider

class ServiceRoutesSpec extends FunSuite with Matchers with ScalatestRouteTest with FailFastCirceSupport with TestPermissions{

  private val category1Deploy = Map("Category1" -> Set(Permission.Deploy))

  private implicit val user: LoggedUser = LoggedUser("1", "admin", category1Deploy)
  private val modelData = ProcessingTypeConfig.read(ConfigWithScalaVersion.streamingProcessTypeConfig).toModelData
  private val serviceRoutes = new ServiceRoutes(mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> modelData))

  implicit val queryResultDecoder: Decoder[QueryResult] = Decoder.decodeJson
      .map(_.hcursor.downField("result").focus.flatMap(_.asString).getOrElse(""))
      .map(QueryResult(_, List.empty))


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
    Post("/service/streaming/enricher", entity) ~> serviceRoutes.securedRoute ~> check {
      status shouldEqual StatusCodes.OK
      val result = entityAs[io.circe.Json]
      result.asObject.flatMap(_.apply("result")).flatMap(_.asString).getOrElse("") shouldEqual "RichObject(parameterValue,123,Optional[rrrr])" //TODO: should be JSON
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
    Post("/service/streaming/enricher", entity) ~> serviceRoutes.securedRoute ~> check {
      status shouldEqual StatusCodes.InternalServerError
      entityAs[JsonThrowable].message shouldEqual Some("ExpressionParseError(EL1041E: After parsing a valid expression, there is still more data in the expression: 'spell',,Some(param),not valid spell expression)")
      entityAs[JsonThrowable].className shouldEqual classOf[ServiceInvocationException].getCanonicalName
    }
  }
  test("display valuable error message for mismatching parameters") {
    val entity = HttpEntity(MediaTypes.`application/json`, "[]")
    Post("/service/streaming/enricher", entity) ~> serviceRoutes.securedRoute ~> check {
      status shouldEqual StatusCodes.InternalServerError
      entityAs[JsonThrowable].message shouldEqual Some( "MissingParameters(Set(param, tariffType),)")
      entityAs[JsonThrowable].className shouldEqual classOf[ServiceInvocationException].getCanonicalName
    }
  }

  test("display valuable error message for missing service") {
    val entity = HttpEntity(MediaTypes.`application/json`, "[]")
    Post("/service/streaming/unexcitingService", entity) ~> serviceRoutes.securedRoute ~> check {
      status shouldEqual StatusCodes.NotFound
      entityAs[JsonThrowable].message shouldEqual Some("Service unexcitingService not found")
      entityAs[JsonThrowable].className shouldEqual classOf[ServiceNotFoundException].getCanonicalName
    }
  }
  test("prevent unauthorized user service invocation") {
    val user = LoggedUser("1", "nonAdmin")
    serviceRoutes.canUserInvokeService(user, "enricher", modelData) shouldBe false
  }
  test("user with category invoke service") {
    val user = LoggedUser("1", "nonAdmin", category1Deploy)
    serviceRoutes.canUserInvokeService(user, "enricher", modelData) shouldBe true
  }
  test("canUserInvokeService always pass unexciting service") {
    val user = LoggedUser("1", "nonAdmin", category1Deploy)
    serviceRoutes.canUserInvokeService(user, "unexcitingService", modelData) shouldBe true
  }

}

object ServiceRoutesSpec {

  @JsonCodec case class Response(age: Int, name: String) extends DisplayJsonWithEncoder[Response]

}
