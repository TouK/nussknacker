package pl.touk.nussknacker.ui.api

import com.typesafe.config.{Config, ConfigFactory}
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.hamcrest.Matchers.{emptyString, is, not}
import org.scalatest.freespec.AnyFreeSpecLike
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.WithDesignerConfig
import pl.touk.nussknacker.test.{NuRestAssureMatchers, PatientScalaFutures, RestAssuredVerboseLogging}

import java.time.Clock

class HttpServiceOAuth2Spec
    extends AnyFreeSpecLike
    with NuItTest
    with WithDesignerConfig
    with NuRestAssureMatchers
    with RestAssuredVerboseLogging
    with PatientScalaFutures {

  private lazy val configuredSymmetricKey = "fooKey"

  private lazy val configuredAudience = "fooAudience"

  override def designerConfig: Config = ScalaMajorVersionConfig.configWithScalaMajorVersion(
    ConfigFactory.parseString(
      s"""authentication:  {
         |  method: "Oidc"
         |  usersFile: "designer/server/src/test/resources/config/simple/basicauth-users.conf"
         |  # FIXME config below
         |  issuer: "https://foo.com"
         |  authorizationEndpoint: "/a"
         |  userinfoEndpoint: "/u"
         |  tokenEndpoint: "/u"
         |  clientId: "fooClientId"
         |  clientSecret: "$configuredSymmetricKey"
         |  audience: "$configuredAudience"
         |}
         |
         |scenarioTypes {}""".stripMargin
    )
  )

  private implicit val clock: Clock = Clock.systemUTC()

  "Some tapir endpoint that requires authenticated user access" - {
    "authenticated using jwt should" - {
      "pass the request to business logic and return successful response" in {
        given()
          .when()
          .auth()
          .oauth2(
            JwtCirce.encode(
              JwtClaim()
                .about("admin")
                .to(configuredAudience)
                .expiresIn(180),
              configuredSymmetricKey,
              JwtAlgorithm.HS256
            )
          )
          .get(s"$nuDesignerHttpAddress/api/user")
          .Then()
          .statusCode(200)
          .body(is(not(emptyString())))
      }
    }
  }

}
