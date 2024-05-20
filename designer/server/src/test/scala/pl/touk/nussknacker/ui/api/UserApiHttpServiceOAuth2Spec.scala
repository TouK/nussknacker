package pl.touk.nussknacker.ui.api

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlPathEqualTo}
import com.typesafe.config.{Config, ConfigFactory}
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import pl.touk.nussknacker.test._
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestCategory
import pl.touk.nussknacker.test.mock.WithWireMockServer

import java.time.Clock

class UserApiHttpServiceOAuth2Spec
    extends AnyFreeSpecLike
    // Warning: WithWireMockServer must be mixed in before NuItTest to avoid NPE during wireMockServerBaseUrl determining in designerConfig method
    with WithWireMockServer
    with NuItTest
    with WithSimplifiedDesignerConfig
    with NuRestAssureMatchers
    with NuRestAssureExtensions
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures {

  private implicit val clock: Clock = Clock.systemUTC()

  private lazy val configuredSymmetricKey = "fooKey"

  private lazy val configuredAudience = "fooAudience"

  private lazy val userInfoEndpointPath = "/userInfo"

  private lazy val userInfoUserId = "userInfoUserId"

  override protected def setupWireMockServer(wireMockServer: WireMockServer): Unit = {
    wireMockServer.stubFor(
      get(urlPathEqualTo(userInfoEndpointPath)).willReturn(
        aResponse()
          .withBody(s"""{"sub": "$userInfoUserId", "roles": ["adminRole"]}""")
      )
    )
  }

  override def designerConfig: Config = super.designerConfig.withValue(
    "authentication",
    ConfigFactory
      .parseString(
        s"""method: "Oidc"
         |# It contains only one rule with role = adminRole and isAdmin = true
         |usersFile: "designer/server/src/test/resources/config/oauth2/users.conf"
         |issuer: "$wireMockServerBaseUrl"
         |userinfoEndpoint: "$userInfoEndpointPath"
         |authorizationEndpoint: "/not-used-but-required-authorization-endpoint"
         |tokenEndpoint: "/not-used-but-required-token-endpoint"
         |clientId: "fooClientId"
         |clientSecret: "$configuredSymmetricKey"
         |audience: "$configuredAudience"
         |""".stripMargin
      )
      .root()
  )

  "The endpoint for getting user info when" - {
    "authenticated using jwt should" - {
      "return user info based on userinfoEndpoint configured in the Oidc configuration" in {
        given()
          .when()
          .auth()
          .oauth2(
            JwtCirce.encode(
              JwtClaim()
                .about("jwtUserId")
                .to(configuredAudience)
                .expiresIn(180),
              configuredSymmetricKey,
              JwtAlgorithm.HS256
            )
          )
          .get(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(200)
          .equalsJsonBody(
            s"""{
               |    "id": "$userInfoUserId",
               |    "username": "$userInfoUserId",
               |    "isAdmin": true,
               |    "categories": ["${TestCategory.Category1.stringify}"],
               |    "categoryPermissions": {},
               |    "globalPermissions": []
               |}""".stripMargin
          )
      }
    }
  }

}
