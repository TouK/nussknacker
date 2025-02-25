package pl.touk.nussknacker.ui.security.oidc

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe.Json
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import sttp.client3.testing.SttpBackendStub

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class OidcAuthenticationResourcesSpec
    extends AnyFunSpec
    with Matchers
    with ScalatestRouteTest
    with FailFastCirceSupport
    with EitherValuesDetailedMessage {

  private val authorizationEndpoint = "http://some.authorization.endpoint"

  private val someClientId = "fooClientId"

  private val defaultAuthConfig = Map(
    "usersFile"             -> "",
    "issuer"                -> "",
    "clientId"              -> someClientId,
    "clientSecret"          -> "",
    "authorizationEndpoint" -> authorizationEndpoint,
    "profileUri"            -> "",
    "accessTokenUri"        -> "",
    "userinfoEndpoint"      -> "",
    "tokenEndpoint"         -> ""
  )

  private def authenticationRoute(authConfig: Map[String, Any]) = {
    implicit val testingBackend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture

    val config = ConfigFactory.parseMap(
      Map(
        "authentication" -> authConfig.asJava
      ).asJava
    )

    OidcAuthenticationProvider.createAuthenticationResources(config, getClass.getClassLoader).routeWithPathPrefix
  }

  it("should return authentication settings") {
    Get(s"/authentication/oidc/settings") ~> authenticationRoute(defaultAuthConfig) ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[Json]
      response.hcursor
        .downField("authorizeUrl")
        .as[String]
        .rightValue shouldBe s"$authorizationEndpoint?client_id=$someClientId&response_type=code&scope=openid+profile"
      response.hcursor.downField("jwtIdTokenNonceVerificationRequired").as[Boolean].rightValue shouldBe false
      response.hcursor.downField("implicitGrantEnabled").as[Boolean].rightValue shouldBe false
      response.hcursor.downField("anonymousAccessAllowed").as[Boolean].rightValue shouldBe false
      response.hcursor.downField("strategy").as[String].rightValue shouldBe "OAuth2"
    }
  }

  it("should return overriden authentication settings") {
    val moduleUrl = "http://some.remote.url"
    val authConfig = defaultAuthConfig + ("overrideFrontendAuthenticationStrategy" -> Map(
      "strategy"  -> "Remote",
      "moduleUrl" -> moduleUrl
    ).asJava)

    Get(s"/authentication/oidc/settings") ~> authenticationRoute(authConfig) ~> check {
      status shouldBe StatusCodes.OK
      val response = responseAs[Json]
      response.hcursor.downField("moduleUrl").as[String].rightValue shouldBe moduleUrl
    }
  }

}
