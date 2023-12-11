package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLogging
}
import pl.touk.nussknacker.ui.api.helpers.{NuItTest, NuScenarioConfigurationHelper, WithMockableDeploymentManager}

class UserApiSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithMockableDeploymentManager
    with NuScenarioConfigurationHelper
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  "The endpoint for getting user info when" - {
    "authenticated should" - {
      "return user info" in {
        given()
          .auth()
          .basic("allpermuser", "allpermuser")
          .when()
          .get(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(200)
          .body(
            equalsJson(s"""{
               |  "id": "allpermuser",
               |  "username": "allpermuser",
               |  "isAdmin": false,
               |  "categories": [ "Category1" ],
               |  "categoryPermissions": {
               |      "Category1": [ "Deploy", "Read", "Write" ]
               |    },
               |  "globalPermissions": []
               |}""".stripMargin)
          )
      }

      "return admin info" in {
        given()
          .auth()
          .basic("admin", "admin")
          .when()
          .get(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(200)
          .body(
            equalsJson(s"""{
                 |  "id": "admin",
                 |  "username": "admin",
                 |  "isAdmin": true,
                 |  "categories": [ "Category1", "Category2" ],
                 |  "categoryPermissions": { },
                 |  "globalPermissions": []
                 |}""".stripMargin)
          )
      }
      "return 405 when invalid HTTP method is passed" in {
        given()
          .auth()
          .basic("admin", "admin")
          .when()
          .put(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(405)
          .body(
            equalTo(
              s"HTTP method not allowed, supported methods: GET"
            )
          )
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .auth()
          .none()
          .when()
          .get(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(401)
          .body(
            equalTo("The resource requires authentication, which was not supplied with the request")
          )
      }
    }
  }

}
