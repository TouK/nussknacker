package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.{`given`, when}
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.NuRestAssureExtensions
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig

class StatisticsApiHttpServiceSecuritySpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with NuRestAssureExtensions {

  private val nuVersion = BuildInfo.version

  "The statistic URL endpoint when" - {
    "require no auth to return single URL" in {
      given()
        .when()
        .noAuth()
        .get(s"$nuDesignerHttpAddress/api/statistic/url")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""
             |{
             |  "urls": ["https://stats.nussknacker.io/?fingerprint=development&source=sources&version=$nuVersion"]
             |}
             |""".stripMargin
        )
    }
  }

}
