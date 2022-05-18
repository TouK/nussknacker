package pl.touk.nussknacker.ui.security.oidc

import cats.data.Validated.Invalid
import org.scalatest.{FunSuite, Matchers}
import pdi.jwt.{JwtBase64, JwtClaim, JwtHeader}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2JwtDecodeRawError, OAuth2JwtKeyDetermineError}
import pl.touk.nussknacker.ui.security.oauth2.OpenIdConnectUserInfo

import java.net.URI

class OidcServiceSpec extends FunSuite with Matchers {

  test("validate jwt format") {
    val validator = OidcService.createJwtValidator(OidcAuthenticationConfiguration(URI.create("http://foo"), issuer = URI.create("http://foo"), clientId = "foo", clientSecret = None))
    validator.introspect[OpenIdConnectUserInfo]("") should matchPattern {
      case Invalid(OAuth2JwtDecodeRawError(_, _)) =>
    }
    validator.introspect[OpenIdConnectUserInfo]("..") should matchPattern {
      case Invalid(OAuth2JwtDecodeRawError(_, _)) =>
    }
    validator.introspect[OpenIdConnectUserInfo]("foo.bar.baz") should matchPattern {
      case Invalid(OAuth2JwtDecodeRawError(_, _)) =>
    }
  }

  test("determine key") {
    val validatorWithoutKey = OidcService.createJwtValidator(OidcAuthenticationConfiguration(URI.create("http://foo"), issuer = URI.create("http://foo"), clientId = "foo", clientSecret = None))
    val tokenWithoutKey = JwtBase64.encodeString(JwtHeader().toJson) + "." + JwtBase64.encodeString(JwtClaim().toJson) + ".baz"
    validatorWithoutKey.introspect[OpenIdConnectUserInfo](tokenWithoutKey) should matchPattern {
      case Invalid(OAuth2JwtKeyDetermineError(_, _)) =>
    }

    val tokenWithKey = JwtBase64.encodeString(JwtHeader().withKeyId("foo").toJson) + "." + JwtBase64.encodeString(JwtClaim().toJson) + ".baz"
    validatorWithoutKey.introspect[OpenIdConnectUserInfo](tokenWithKey) should matchPattern {
      case Invalid(OAuth2JwtKeyDetermineError(_, _)) =>
    }

    val validatorWithInvalidJwk = OidcService.createJwtValidator(OidcAuthenticationConfiguration(URI.create("http://foo"), issuer = URI.create("http://foo"), clientId = "foo", clientSecret = None, jwksUri = Some(URI.create("/foo"))))
    validatorWithInvalidJwk.introspect[OpenIdConnectUserInfo](tokenWithKey) should matchPattern {
      case Invalid(OAuth2JwtKeyDetermineError(_, _)) =>
    }
  }

  // TODO: tests for token claim parsing

}
