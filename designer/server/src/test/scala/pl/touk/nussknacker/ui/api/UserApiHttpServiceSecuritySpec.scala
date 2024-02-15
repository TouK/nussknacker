package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLogging}
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.{WithRichConfigRestAssuredUsersExtensions, WithRichDesignerConfig}

class UserApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithRichDesignerConfig
    with WithRichConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  "The endpoint for getting user info when" - {
    "authenticated should" - {
      "return user info" in {
        given()
          .when()
          .basicAuthLimitedReader()
          .get(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(200)
          .equalsJsonBody(s"""{
             |  "id": "limitedReader",
             |  "username": "limitedReader",
             |  "isAdmin": false,
             |  "categories": [ "Category1" ],
             |  "categoryPermissions": {
             |      "Category1": [ "Read" ]
             |    },
             |  "globalPermissions": []
             |}""".stripMargin)
      }
    }
    "not authenticated should" - {
      "forbid access" in {
        given()
          .when()
          .basicAuthUnknownUser()
          .get(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(401)
          .body(equalTo("The supplied authentication is invalid"))
      }
    }
  }

}
