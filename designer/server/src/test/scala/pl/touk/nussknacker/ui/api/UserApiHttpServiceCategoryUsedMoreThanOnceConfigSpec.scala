package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig
import pl.touk.nussknacker.test.config.WithCategoryUsedMoreThanOnceDesignerConfig.TestCategory
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLogging}

class UserApiHttpServiceCategoryUsedMoreThanOnceConfigSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithCategoryUsedMoreThanOnceDesignerConfig
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  "In designer configured with multiple processing types using the same category" - {
    "The endpoint for getting user info should" - {
      "return not duplicated categories" in {
        given()
          .when()
          .auth()
          .basic("admin", "admin")
          .get(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(200)
          .equalsJsonBody(s"""{
               |  "id": "admin",
               |  "username": "admin",
               |  "isAdmin": true,
               |  "categories": ["${TestCategory.Category1}"],
               |  "categoryPermissions": {},
               |  "globalPermissions": []
               |}""".stripMargin)
      }
    }
  }

}
