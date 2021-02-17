package pl.touk.nussknacker.ui.security.oauth2

import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.oauth2.BaseOAuth2Service.millisToDeadline
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.OAuth2CompoundException

import scala.concurrent.duration.Deadline
import scala.concurrent.{ExecutionContext, Future}

trait JwtStandardClaims {
  val iss: Option[String]
  val sub: Option[String]
  val aud: Option[String]
  val exp: Option[Long]
  val nbf: Option[Long]
  val iat: Option[Long]
  val jti: Option[String]
}

class JwtOAuth2Service[
  ProfileResponse: Decoder,
  AccessTokenResponse <: StandardAccessTokenResponse : Decoder,
  DecodedJwtAccessToken <: JwtStandardClaims : Decoder
](clientApi: OAuth2ClientApi[ProfileResponse, AccessTokenResponse],
  createLoggedUser: (ProfileResponse) => LoggedUser,
  configuration: OAuth2Configuration)
 (implicit ec: ExecutionContext)
  extends BaseOAuth2Service[ProfileResponse, AccessTokenResponse](clientApi, createLoggedUser, configuration)
    with LazyLogging {

  protected val introspectAccessToken: Boolean = configuration.jwt.exists(_.accessTokenIsJwt)

  protected val jwtValidator: Option[JwtValidator] = configuration.jwt.map(_.authServerPublicKey).map(new JwtValidator(_))

  protected def introspectToken[Claims : Decoder](token: String): Option[Claims] =
    jwtValidator.map(_.introspect[Claims](token) match {
      case Valid(claims) => claims
      case Invalid(jwtErrors) => throw OAuth2CompoundException(jwtErrors)
    })

  override protected def getProfile(accessToken: String, expiration: Option[Deadline]): Future[(ProfileResponse, Option[Deadline])] = {
    val expirationFromToken =
      Some(accessToken).filter(_ => introspectAccessToken).flatMap(introspectToken[DefaultJwtAccessToken])
        .flatMap(_.exp).map(millisToDeadline)
    super.getProfile(accessToken, expirationFromToken.orElse(expiration))
  }
}

