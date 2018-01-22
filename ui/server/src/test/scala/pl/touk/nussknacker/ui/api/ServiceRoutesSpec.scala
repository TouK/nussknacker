package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import argonaut.{DecodeJson, DecodeResult, Json}
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.DisplayableAsJson
import pl.touk.nussknacker.engine.management.FlinkModelData
import pl.touk.nussknacker.ui.api.ServiceRoutes.JsonThrowable
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import argonaut._
import Argonaut._
import ArgonautShapeless._
import pl.touk.nussknacker.engine.util.service.query.ExpressionServiceQuery.ParametersCompilationException
import pl.touk.nussknacker.engine.util.service.query.ServiceQuery.ServiceNotFoundException
import pl.touk.nussknacker.ui.util.Argonaut62Support


class ServiceRoutesSpec extends FlatSpec with Matchers with ScalatestRouteTest with Argonaut62Support{

  private implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
  private implicit val user = LoggedUser("admin", Permission.Admin :: Nil, Nil)
  private val serviceRoutes = new ServiceRoutes(Map(ProcessingType.Streaming -> FlinkModelData(ConfigFactory.load())))

  it should "invoke service" in {
    val entity = HttpEntity(MediaTypes.`application/json`,
      """
        |[
        | {
        |    "name": "param",
        |    "expression": {
        |       "language":"spel",
        |       "expression":"'parameterValue'"
        |    }
        | }
        |]
      """.stripMargin)
    Post("/service/streaming/enricher", entity) ~> serviceRoutes.route ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual "RichObject(parameterValue,123,Some(rrrr))" //TODO: should be JSON

    }
  }
  it should "display valuable error message for invalid spell expression" in {
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
  it should "display valuable error message for mismatching parameters" in {
    val entity = HttpEntity(MediaTypes.`application/json`, "[]")
    Post("/service/streaming/enricher", entity) ~> serviceRoutes.route ~> check {
      status shouldEqual StatusCodes.InternalServerError
      entityAs[JsonThrowable].message shouldEqual Some( "Missing parameter: param")
      entityAs[JsonThrowable].className shouldEqual classOf[IllegalArgumentException].getCanonicalName
    }
  }
  it should "display valuable error message for missing service" in {
    val entity = HttpEntity(MediaTypes.`application/json`, "[]")
    Post("/service/streaming/unexcitingService", entity) ~> serviceRoutes.route ~> check {
      status shouldEqual StatusCodes.NotFound
      entityAs[JsonThrowable].message shouldEqual Some("service unexcitingService not found")
      entityAs[JsonThrowable].className shouldEqual classOf[ServiceNotFoundException].getCanonicalName
    }
  }
}

object ServiceRoutesSpec {

  import argonaut._, Argonaut._, ArgonautShapeless._

  case class Response(age: Int, name: String) extends DisplayableAsJson[Response]

}
