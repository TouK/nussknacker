package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.Json._
import org.scalatest._
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.EspItTest

class UsersResourcesSpec extends FunSuite with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with EspItTest with PatientScalaFutures {

  private val usersRoute = new UserResources(processCategoryService)

  test("fetch user info") {
    getUser(isAdmin = false) ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Json] shouldBe obj(
        "id" -> fromString("1"),
        "username" -> fromString("user"),
        "isAdmin" -> fromBoolean(false),
        "categories" -> arr(
          List("Category1", "Category2", "ReqRes", "TESTCAT", "TESTCAT2").map(fromString): _*
        ),
        "categoryPermissions" -> obj(
          "TESTCAT" -> arr(
            List("Deploy", "Read", "Write").map(fromString): _*
          ),
          "TESTCAT2" -> arr(
            List("Deploy", "Read", "Write").map(fromString): _*
          )
        ),
        "globalPermissions" -> arr(fromString("CustomFixedPermission"))
      )
    }
  }

  test("fetch admin info") {
    getUser(isAdmin = true) ~> check {
      status shouldBe StatusCodes.OK
      responseAs[Json] shouldBe obj(
        "id" -> fromString("1"),
        "username" -> fromString("admin"),
        "isAdmin" -> fromBoolean(true),
        "categories" -> arr(
          List("Category1", "Category2", "ReqRes", "TESTCAT", "TESTCAT2").map(fromString): _*
        ),
        "categoryPermissions" -> obj(),
        "globalPermissions" -> arr()
      )
    }
  }

  private def getUser(isAdmin: Boolean): RouteTestResult =
    Get("/user") ~> routeWithPermissions(usersRoute, isAdmin)

}
