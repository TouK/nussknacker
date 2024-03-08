package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.{emptyString, is, not}
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.WithOAuth2DesignerConfig
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLogging}

class UserApiHttpServiceOAuth2Spec
    extends AnyFreeSpecLike
    with NuItTest
    with WithOAuth2DesignerConfig
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  "The endpoint for getting user info should" - {
    "authenticated using jwt should" - {
      "return user info" in {
        given()
          .when()
          .auth()
          // FIXME correct token
          .oauth2("foo.bar.baz")
          .get(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(200)
          .body(is(not(emptyString())))
      }
    }
  }

}
