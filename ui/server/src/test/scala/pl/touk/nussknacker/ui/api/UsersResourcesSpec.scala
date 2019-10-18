package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.scalatest._
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.ui.api.helpers.EspItTest

class UsersResourcesSpec extends FunSuite with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with EspItTest {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(100, Millis)))

  test("fetch user info") {
    getUser ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Json] shouldBe Json.obj(
        "id" -> Json.fromString("userId"),
        "isAdmin" -> Json.fromBoolean(false),
        "categories" -> Json.arr(
          List(
            "Category2",
            "ReqRes",
            "Category1",
            "TESTCAT",
            "TESTCAT2"
          ).map(Json.fromString): _*
        ),
        "categoryPermissions" -> Json.obj(
          "TESTCAT" -> Json.arr(
            List(
              "Deploy",
              "Read",
              "Write"
            ).map(Json.fromString): _*),
          "TESTCAT2" -> Json.arr(
            List(
              "Deploy",
              "Read",
              "Write"
            ).map(Json.fromString): _*)
        ),
        "globalPermissions" -> Json.obj(
          "adminTab" -> Json.fromBoolean(false)
        )
      )
    }
  }
}
