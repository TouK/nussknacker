package pl.touk.nussknacker.ui.security.oauth2

import cats.data.ValidatedNel
import cats.implicits._
import io.circe.Decoder
import pdi.jwt.JwtCirce

import scala.util.{Failure, Success}


class JwtValidator[ProfileResponse: Decoder](jwtConfiguration: JwtConfiguration) {
  def getProfileFromJwt(token: String): ValidatedNel[String, ProfileResponse] =
    JwtCirce.decodeJson(token, jwtConfiguration.authServerPublicKey) match {
      case Success(json) => json.as[ProfileResponse] match {
        case Left(failure) => s"JwtValidator: failure in decoding json as ProfileResponse: ${failure.getLocalizedMessage}".invalidNel
        case Right(profile) => profile.validNel
      }
      case Failure(ex) => s"JwtValidator: failure in JwtCirce.decodeJson: ${ex.getLocalizedMessage}".invalidNel
    }
}
