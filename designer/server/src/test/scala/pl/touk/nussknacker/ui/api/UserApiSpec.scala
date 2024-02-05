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
import pl.touk.nussknacker.ui.api.helpers.{NuItTest, NuTestScenarioManager, WithMockableDeploymentManager}

class UserApiSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithMockableDeploymentManager
    with NuTestScenarioManager
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  "The endpoint for getting user info when" - {
    "authenticated should" - {
      "return user info" in {
        given()
          .basicAuth("allpermuser", "allpermuser")
          .when()
          .get(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(200)
          .equalsJsonBody(s"""{
             |  "id": "allpermuser",
             |  "username": "allpermuser",
             |  "isAdmin": false,
             |  "categories": [ "Category1" ],
             |  "categoryPermissions": {
             |      "Category1": [ "Deploy", "Read", "Write" ]
             |    },
             |  "globalPermissions": []
             |}""".stripMargin)
      }
      "return admin info" in {
        given()
          .basicAuth("admin", "admin")
          .when()
          .get(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(200)
          .equalsJsonBody(s"""{
             |  "id": "admin",
             |  "username": "admin",
             |  "isAdmin": true,
             |  "categories": [ "Category1", "Category2" ],
             |  "categoryPermissions": { },
             |  "globalPermissions": []
             |}""".stripMargin)
      }
      "return 405 when invalid HTTP method is passed" in {
        given()
          .basicAuth("admin", "admin")
          .when()
          .put(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(405)
          .body(
            equalTo(
              s"Method Not Allowed"
            )
          )
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .noAuth()
          .when()
          .basicAuth("unknown-user", "wrong-password")
          .get(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
    "no credentials were passed should" - {
      "authenticate as anonymous" in {
        given()
          .noAuth()
          .when()
          .get(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""
               |{
               |  "id": "anonymous",
               |  "username": "anonymous",
               |  "isAdmin": false,
               |  "categories": [ "Category2" ],
               |  "categoryPermissions": {
               |    "Category2": [ "Read" ]
               |  },
               |  "globalPermissions": []
               |}
               |""".stripMargin
          )
      }
    }
  }

}
