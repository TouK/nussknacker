package pl.touk.nussknacker.ui.security.oauth2

import java.security.PublicKey
import cats.data.ValidatedNel
import cats.implicits._
import io.circe.Decoder
import pdi.jwt.{JwtCirce, JwtOptions}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2Error, OAuth2JwtError}

import scala.util.{Failure, Success, Try}

class JwtValidator(keyProvider: Option[String] => PublicKey) {
  def introspect[TokenClaims: Decoder](token: String): ValidatedNel[OAuth2ErrorHandler.OAuth2Error, TokenClaims] = {
    JwtCirce.decodeAll(token, JwtOptions.DEFAULT.copy(signature = false))
      .flatMap { case (header, _, _) => Try(keyProvider(header.keyId)) }
      .flatMap { publicKey => JwtCirce.decodeJson(token, publicKey) }
    match {
      case Success(json) => json.as[TokenClaims] match {
        case Left(failure) => OAuth2JwtError(s"JwtValidator: failure in decoding json: ${failure.getLocalizedMessage}").invalidNel
        case Right(profile) => profile.validNel
      }
      case Failure(ex) =>
        OAuth2JwtError(s"JwtValidator: failure in JwtCirce.decodeJson: ${ex.getLocalizedMessage}").invalidNel
    }
  }
}
