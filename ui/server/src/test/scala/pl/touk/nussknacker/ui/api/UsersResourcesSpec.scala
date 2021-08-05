package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.Json.fromString
import org.scalatest._
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.EspItTest

class UsersResourcesSpec extends FunSuite with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with EspItTest with PatientScalaFutures {

  test("fetch user info") {
    getUser(isAdmin = false) ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Json] shouldBe Json.obj(
        "id" -> fromString("1"),
        "username" -> fromString("user"),
        "isAdmin" -> Json.fromBoolean(false),
        "categories" -> Json.arr(
          List("Category1", "Category2", "ReqRes", "TESTCAT", "TESTCAT2").map(fromString): _*
        ),
        "categoryPermissions" -> Json.obj(
          "TESTCAT" -> Json.arr(
            List("Deploy", "Read", "Write").map(fromString): _*
          ),
          "TESTCAT2" -> Json.arr(
            List("Deploy", "Read", "Write").map(fromString): _*
          )
        ),
        "globalPermissions" -> Json.arr(fromString("CustomFixedPermission"))
      )
    }
  }

  test("fetch admin info") {
    getUser(isAdmin = true) ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Json] shouldBe Json.obj(
        "id" -> fromString("1"),
        "username" -> fromString("admin"),
        "isAdmin" -> Json.fromBoolean(true),
        "categories" -> Json.arr(
          List("Category1", "Category2", "ReqRes", "TESTCAT", "TESTCAT2").map(fromString): _*
        ),
        "categoryPermissions" -> Json.obj(),
        "globalPermissions" -> Json.arr()
      )
    }
  }
}
