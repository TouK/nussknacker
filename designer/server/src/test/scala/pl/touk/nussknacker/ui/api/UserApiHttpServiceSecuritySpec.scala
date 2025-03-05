package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.equalTo
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.{
  WithAccessControlCheckingConfigRestAssuredUsersExtensions,
  WithAccessControlCheckingDesignerConfig
}

class UserApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigRestAssuredUsersExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLoggingIfValidationFails
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
    "no credentials were passed should" - {
      "authenticate as anonymous and return anonymous user info" in {
        given()
          .when()
          .noAuth()
          .get(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(200)
          .equalsJsonBody(s"""{
               |  "id": "anonymous",
               |  "username": "anonymous",
               |  "isAdmin": false,
               |  "categories": [ "Category2" ],
               |  "categoryPermissions": {
               |      "Category2": [ "Read" ]
               |    },
               |  "globalPermissions": []
               |}""".stripMargin)
      }
      "impersonating user has permission to impersonate should" - {
        "return impersonated user info" in {
          given()
            .when()
            .basicAuthAllPermUser()
            .impersonateLimitedReaderUser()
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
      "impersonating user does not have permission to impersonate should" - {
        "forbid access" in {
          given()
            .when()
            .basicAuthWriter()
            .impersonateLimitedReaderUser()
            .get(s"$nuDesignerHttpAddress/api/user")
            .Then()
            .statusCode(403)
            .body(equalTo("The supplied authentication is not authorized to impersonate"))
        }
      }
    }
    "anonymous user credentials are passed directly should not authenticate the request" in {
      given()
        .when()
        .basicAuth("anonymous", "anonymous")
        .basicAuthUnknownUser()
        .get(s"$nuDesignerHttpAddress/api/user")
        .Then()
        .statusCode(401)
        .body(equalTo("The supplied authentication is invalid"))
    }
  }

}
