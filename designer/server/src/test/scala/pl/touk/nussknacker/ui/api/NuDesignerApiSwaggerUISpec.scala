package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.containsString
import org.scalatest.freespec.AnyFreeSpec
import pl.touk.nussknacker.test.RestAssuredVerboseLogging
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig

class NuDesignerApiSwaggerUISpec
    extends AnyFreeSpec
    with NuItTest
    with WithSimplifiedDesignerConfig
    with RestAssuredVerboseLogging {

  "Swagger UI should be visible and achievable" in {
    given()
      .when()
      .get(s"$nuDesignerHttpAddress/api/docs")
      .Then()
      .statusCode(200)
      .header("Content-Type", "text/html")
      .body(containsString("Swagger UI"))
  }

}
