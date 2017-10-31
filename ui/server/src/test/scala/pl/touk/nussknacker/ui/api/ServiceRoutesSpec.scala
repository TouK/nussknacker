package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.DisplayableAsJson
import pl.touk.nussknacker.engine.management.FlinkModelData
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

class ServiceRoutesSpec extends FlatSpec with Matchers with ScalatestRouteTest {

  import ServiceRoutes._
  import ServiceRoutesSpec._

  private implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
  private implicit val user = LoggedUser("admin", Permission.Admin :: Nil, Nil)
  val serviceRoutes = new ServiceRoutes(Map(ProcessingType.Streaming -> FlinkModelData()))

  it should "invoke service" in {
    val entity = HttpEntity(MediaTypes.`application/json`,
      """
        |{
        |  "param": "parameterValue"
        |}
      """.stripMargin)
    Post("/service/streaming/enricher", entity) ~> serviceRoutes.route ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual "\"RichObject(parameterValue,123,Some(rrrr))\"" //TODO: should be JSON

    }
  }
  it should "display valuable error message for mismatching parameters" in {
    val entity = HttpEntity(MediaTypes.`application/json`, "{}")
    Post("/service/streaming/enricher", entity) ~> serviceRoutes.route ~> check {
      status shouldEqual StatusCodes.BadRequest
      responseAs[String] shouldEqual "Illegal argument Missing parameter: param"
    }
  }
  it should "display valuable error message for missing service" in {
    val entity = HttpEntity(MediaTypes.`application/json`, "{}")
    Post("/service/streaming/unexcitingService", entity) ~> serviceRoutes.route ~> check {
      status shouldEqual StatusCodes.NotFound
      responseAs[String] shouldEqual "Service 'unexcitingService' not found."
    }
  }
  it should "copy Any to JSON" in {
    val response: Any = Response(22, "Joe")
    anyToJson(response) shouldBe
      """
        |{"name":"Joe","age":22}
      """.stripMargin.trim
  }
}

object ServiceRoutesSpec {

  import argonaut._, Argonaut._, ArgonautShapeless._

  case class Response(age: Int, name: String) extends DisplayableAsJson[Response]

}
