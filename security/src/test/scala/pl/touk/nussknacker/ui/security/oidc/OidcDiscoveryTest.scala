package pl.touk.nussknacker.ui.security.oidc

import org.scalatest.{FlatSpec, Matchers}
import sttp.client.testing.SttpBackendStub

import java.net.URI

class OidcDiscoveryTest extends FlatSpec with Matchers {

  import io.circe.syntax._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val discoveredConfiguration = OidcDiscovery(
    issuer = URI.create("https://issuer"),
    authorizationEndpoint = URI.create("https://issuer/authorize"),
    tokenEndpoint = URI.create("https://issuer/token"),
    userinfoEndpoint = URI.create("https://issuer/userinfo"),
    jwksUri = URI.create("https://issuer/.well-known/jwks.json"),
    scopesSupported = None,
    responseTypesSupported = List("code")
  )

  implicit private val sttp = SttpBackendStub.asynchronousFuture
    .whenRequestMatches(_.uri.host == "exception")
    .thenRespond(throw new Exception("fatal error"))
    .whenRequestMatches(_.uri.host == "client-error")
    .thenRespondNotFound()
    .whenRequestMatches(_.uri.host == "correct")
    .thenRespond(discoveredConfiguration.asJson.toString())

  it should "handle both a success and failures" in {
    OidcDiscovery(URI.create("http://exception")) shouldEqual None
    OidcDiscovery(URI.create("http://client-error")) shouldEqual None
    OidcDiscovery(URI.create("http://server-error")) shouldEqual None
    OidcDiscovery(URI.create("http://correct")) shouldEqual Some(discoveredConfiguration)
  }

}
