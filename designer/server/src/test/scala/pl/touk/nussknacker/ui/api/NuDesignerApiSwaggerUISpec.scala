package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.containsString
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.test.base.it.{NuItTest, WithMockableDeploymentManager}
import pl.touk.nussknacker.test.utils.domain.NuTestScenarioManager
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  NuRestAssureMatchers,
  PatientScalaFutures,
  RestAssuredVerboseLogging
}

class NuDesignerApiSwaggerUISpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithMockableDeploymentManager
    with NuTestScenarioManager
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

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
