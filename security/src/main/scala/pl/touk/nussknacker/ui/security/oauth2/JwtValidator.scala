package pl.touk.nussknacker.ui.security.oauth2

import cats.data.Validated
import cats.implicits._
import io.circe.Decoder
import pdi.jwt.{JwtCirce, JwtOptions}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2JwtDecodeClaimsError, OAuth2JwtDecodeClaimsJsonError, OAuth2JwtDecodeRawError, OAuth2JwtKeyDetermineError, ParsedToken}

import java.security.PublicKey
import scala.util.Try

class JwtValidator(keyProvider: Option[String] => PublicKey) {

  def introspect[TokenClaims: Decoder](token: String): Validated[OAuth2ErrorHandler.OAuth2JwtError, TokenClaims] = {
    (for {
      parsedToken <- parsedTokenWithoutSignature(token)
      publicKey <- Try(keyProvider(parsedToken.header.keyId)).toEither.leftMap(OAuth2JwtKeyDetermineError(parsedToken, _))
      tokenClaimsJson <- JwtCirce.decodeJson(token, publicKey).toEither.leftMap(OAuth2JwtDecodeClaimsError(parsedToken, publicKey, _))
      parsedTokenClaims <- tokenClaimsJson.as[TokenClaims].leftMap(OAuth2JwtDecodeClaimsJsonError(parsedToken, publicKey, tokenClaimsJson, _))
    } yield parsedTokenClaims).toValidated
  }

  private def parsedTokenWithoutSignature(token: String) = {
    JwtCirce.decodeAll(token, JwtOptions.DEFAULT.copy(signature = false)).toEither.leftMap(OAuth2JwtDecodeRawError(token, _)).map(ParsedToken.apply _ tupled)
  }

}
