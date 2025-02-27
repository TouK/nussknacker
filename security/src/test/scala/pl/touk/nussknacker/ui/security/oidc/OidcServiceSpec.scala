package pl.touk.nussknacker.ui.security.oidc

import cats.data.Validated.Invalid
import org.scalatest.{Inside, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pdi.jwt._
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{
  OAuth2JwtDecodeClaimsError,
  OAuth2JwtDecodeClaimsJsonError,
  OAuth2JwtDecodeRawError,
  OAuth2JwtKeyDetermineError
}

import java.net.URI

class OidcServiceSpec extends AnyFunSuite with Matchers with EitherValuesDetailedMessage with OptionValues with Inside {

  test("validate jwt format") {
    val validator = OidcService.createJwtValidator(
      OidcAuthenticationConfiguration(
        URI.create("http://foo"),
        issuer = URI.create("http://foo"),
        clientId = "foo",
        clientSecret = None,
      )
    )
    validator.introspect[OidcUserInfo]("") should matchPattern { case Invalid(OAuth2JwtDecodeRawError(_, _)) =>
    }
    validator.introspect[OidcUserInfo]("..") should matchPattern { case Invalid(OAuth2JwtDecodeRawError(_, _)) =>
    }
    inside(validator.introspect[OidcUserInfo]("foo.barbar.bazbaz")) { case Invalid(err: OAuth2JwtDecodeRawError) =>
      err.msg should not include "barbar"
      err.msg should include("ba**ar")
      err.msg should not include "bazbaz"
      err.msg should include("ba**az")
    }
  }

  test("determine key") {
    val validatorWithoutKey = OidcService.createJwtValidator(
      OidcAuthenticationConfiguration(
        URI.create("http://foo"),
        issuer = URI.create("http://foo"),
        clientId = "foo",
        clientSecret = None
      )
    )
    val claim = JwtClaim().about("Foo Bar") + ("email", "foo@bar.com")
    val tokenWithoutKey =
      JwtBase64.encodeString(JwtHeader().toJson) + "." + JwtBase64.encodeString(claim.toJson) + ".baz"
    inside(validatorWithoutKey.introspect[OidcUserInfo](tokenWithoutKey)) {
      case Invalid(err: OAuth2JwtKeyDetermineError) =>
        err.msg should not include "Foo Bar"
        err.msg should not include "foo@bar.com"
    }

    val tokenWithKey = JwtBase64.encodeString(JwtHeader().withKeyId("foo").toJson) + "." + JwtBase64.encodeString(
      JwtClaim().toJson
    ) + ".baz"
    validatorWithoutKey.introspect[OidcUserInfo](tokenWithKey) should matchPattern {
      case Invalid(OAuth2JwtKeyDetermineError(_, _)) =>
    }

    val validatorWithInvalidJwk = OidcService.createJwtValidator(
      OidcAuthenticationConfiguration(
        URI.create("http://foo"),
        issuer = URI.create("http://foo"),
        clientId = "foo",
        clientSecret = None,
        jwksUri = Some(URI.create("/foo"))
      )
    )
    validatorWithInvalidJwk.introspect[OidcUserInfo](tokenWithKey) should matchPattern {
      case Invalid(OAuth2JwtKeyDetermineError(_, _)) =>
    }
  }

  test("symmetrically encoded tokens") {
    val secretKey = "secret"
    val name      = "Foo Bar"
    val audience  = "http://foo"
    val validator = OidcService.createJwtValidator(
      OidcAuthenticationConfiguration(
        URI.create("http://foo"),
        issuer = URI.create("http://foo"),
        clientId = "foo",
        clientSecret = Some(secretKey)
      )
    )

    val validToken = JwtCirce.encode(JwtClaim().about(name).to(audience), secretKey, JwtAlgorithm.HS256)
    val result     = validator.introspect[OidcUserInfo](validToken).toEither.rightValue
    result.subject.value shouldEqual name
    result.audience.value.rightValue shouldEqual audience

    inside(validator.introspect[String](validToken)) { case Invalid(err: OAuth2JwtDecodeClaimsJsonError) =>
      err.msg should not include "Foo Bar"
    }

    val invalidToken = JwtCirce.encode(JwtClaim().about(name).to(audience), "invalid", JwtAlgorithm.HS256)
    inside(validator.introspect[OidcUserInfo](invalidToken)) { case Invalid(err: OAuth2JwtDecodeClaimsError) =>
      err.msg should not include "Foo Bar"
    }
  }

  // TODO: tests for asymmetric encoding

}
