package pl.touk.nussknacker.ui.security.oauth2

import cats.data.ValidatedNel
import cats.implicits._
import io.circe.Decoder
import pdi.jwt.JwtCirce
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2Error, OAuth2JwtError}

import scala.util.{Failure, Success}


class JwtValidator[ProfileResponse: Decoder](jwtConfiguration: JwtConfiguration) {
  def getProfileFromJwt(token: String): ValidatedNel[OAuth2Error, ProfileResponse] =
    JwtCirce.decodeJson(token, jwtConfiguration.authServerPublicKey) match {
      case Success(json) => json.as[ProfileResponse] match {
        case Left(failure) => OAuth2JwtError(s"JwtValidator: failure in decoding json as ProfileResponse: ${failure.getLocalizedMessage}").invalidNel
        case Right(profile) => profile.validNel
      }
      case Failure(ex) => OAuth2JwtError(s"JwtValidator: failure in JwtCirce.decodeJson: ${ex.getLocalizedMessage}").invalidNel
    }
}
