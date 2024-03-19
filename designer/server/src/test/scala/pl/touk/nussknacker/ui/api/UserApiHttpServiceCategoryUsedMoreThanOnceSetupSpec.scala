package pl.touk.nussknacker.ui.api

import com.typesafe.config.{Config, ConfigFactory}
import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.{
  WithDesignerConfig,
  WithSimplifiedConfigRestAssuredUsersExtensions,
  WithSimplifiedDesignerConfig
}
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLogging
}

class UserApiHttpServiceCategoryUsedMoreThanOnceSetupSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithDesignerConfig
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  override def designerConfig: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(
    ConfigFactory.parseResources("config/category-used-more-than-once/category-used-more-than-once-designer.conf")
  )

  "In designer setup with multiple processing types using the same category" - {
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
               |  "categories": ["Category1"],
               |  "categoryPermissions": {},
               |  "globalPermissions": []
               |}""".stripMargin)
      }
    }
  }

}
