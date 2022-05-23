package pl.touk.nussknacker.ui.security.oauth2.jwt

import cats.data.Validated
import cats.implicits._
import io.circe.Decoder
import pdi.jwt.{JwtCirce, JwtHeader, JwtOptions}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2JwtDecodeClaimsError, OAuth2JwtDecodeClaimsJsonError, OAuth2JwtDecodeRawError, OAuth2JwtKeyDetermineError}

import java.security.{Key, PublicKey}
import javax.crypto.SecretKey
import scala.util.Try

class JwtValidator(keyProvider: JwtHeader => Key) {

  def introspect[TokenClaims: Decoder](token: String): Validated[OAuth2ErrorHandler.OAuth2JwtError, TokenClaims] = {
    (for {
      parsedToken <- parsedTokenWithoutSignature(token)
      key <- Try(keyProvider(parsedToken.header)).toEither.leftMap(OAuth2JwtKeyDetermineError(parsedToken, _))
      tokenClaimsJson <- decodeUsingKey(token, key).toEither.leftMap(OAuth2JwtDecodeClaimsError(parsedToken, key, _))
      parsedTokenClaims <- tokenClaimsJson.as[TokenClaims].leftMap(OAuth2JwtDecodeClaimsJsonError(parsedToken, key, tokenClaimsJson, _))
    } yield parsedTokenClaims).toValidated
  }

  private def decodeUsingKey(token: String, key: Key) = {
    key match {
      case publicKey: PublicKey => JwtCirce.decodeJson(token, publicKey)
      case secretKey: SecretKey => JwtCirce.decodeJson(token, secretKey)
      case other => throw new IllegalArgumentException(s"Not supported key: $other")
    }
  }

  private def parsedTokenWithoutSignature(token: String) = {
    JwtCirce.decodeAll(token, JwtOptions.DEFAULT.copy(signature = false)).toEither.bimap(OAuth2JwtDecodeRawError(RawJwtToken(token), _), ParsedJwtToken.apply _ tupled)
  }

}
