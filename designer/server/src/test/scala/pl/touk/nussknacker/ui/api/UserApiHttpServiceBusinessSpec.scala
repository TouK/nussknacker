package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.{WithBusinessCaseRestAssuredUsersExtensions, WithSimplifiedDesignerConfig}

class UserApiHttpServiceBusinessSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithBusinessCaseRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures {

  "The endpoint for getting user info should" - {
    "return user info" in {
      given()
        .when()
        .basicAuthAllPermUser()
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
             |  "globalPermissions": [ "Impersonate" ]
             |}""".stripMargin)
    }
    "return admin info" in {
      given()
        .when()
        .basicAuthAdmin()
        .get(s"$nuDesignerHttpAddress/api/user")
        .Then()
        .statusCode(200)
        .equalsJsonBody(s"""{
             |  "id": "admin",
             |  "username": "admin",
             |  "isAdmin": true,
             |  "categories": [ "Category1" ],
             |  "categoryPermissions": { },
             |  "globalPermissions": []
             |}""".stripMargin)
    }
    "return 405 when invalid HTTP method is passed" in {
      given()
        .when()
        .basicAuthAdmin()
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

}
