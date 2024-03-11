package pl.touk.nussknacker.ui.api

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlPathEqualTo}
import com.typesafe.config.{Config, ConfigFactory}
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.WithDesignerConfig
import pl.touk.nussknacker.test._

import java.time.Clock

class UserApiHttpServiceOAuth2Spec
    extends AnyFreeSpecLike
    with NuItTest
    with WithDesignerConfig
    with NuRestAssureMatchers
    with NuRestAssureExtensions
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  private implicit val clock: Clock = Clock.systemUTC()

  private lazy val configuredSymmetricKey = "fooKey"

  private lazy val configuredAudience = "fooAudience"

  private lazy val userInfoEndpointPath = "/userInfo"

  private lazy val userInfoUserId = """userInfoUserId"""

  private lazy val wireMockServer: WireMockServer = {
    val server = AvailablePortFinder.withAvailablePortsBlocked(1)(l => new WireMockServer(l.head))
    server.start()
    server.stubFor(
      get(urlPathEqualTo(userInfoEndpointPath)).willReturn(
        aResponse()
          .withBody(s"""{"sub": "$userInfoUserId", "roles": ["adminRole"]}""")
      )
    )
    server
  }

  override def designerConfig: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(
    ConfigFactory.parseString(
      s"""authentication:  {
         |  method: "Oidc"
         |  # It contains only one rule with role = adminRole and isAdmin = true
         |  usersFile: "designer/server/src/test/resources/config/oauth2/users.conf"
         |  issuer: "${wireMockServer.baseUrl()}"
         |  userinfoEndpoint: "$userInfoEndpointPath"
         |  authorizationEndpoint: "/not-used-but-required-authorization-endpoint"
         |  tokenEndpoint: "/not-used-but-required-token-endpoint"
         |  clientId: "fooClientId"
         |  clientSecret: "$configuredSymmetricKey"
         |  audience: "$configuredAudience"
         |}
         |
         |scenarioTypes {}""".stripMargin
    )
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
               |    "categories": [],
               |    "categoryPermissions": {},
               |    "globalPermissions": []
               |}""".stripMargin
          )
      }
    }
  }

  override protected def afterAll(): Unit = {
    wireMockServer.shutdown()
    super.afterAll()
  }

}
