package pl.touk.nussknacker.ui.security.oauth2

import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultAsyncCache, ExpiryConfig}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ClientApi.DefaultAccessTokenResponse
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.OAuth2CompoundException

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Deadline, FiniteDuration, MILLISECONDS}

trait JwtStandardClaims {
  val iss: Option[String]
  val sub: Option[String]
  val aud: Option[String]
  val exp: Option[Long]
  val nbf: Option[Long]
  val iat: Option[Long]
  val jti: Option[String]
}

class DefaultOAuth2Service[
  ProfileResponse: Decoder,
  DecodedJwtAccessToken <: JwtStandardClaims : Decoder
](clientApi: OAuth2ClientApi[ProfileResponse, DefaultAccessTokenResponse],
  oAuth2Profile: OAuth2Profile[ProfileResponse],
  configuration: OAuth2Configuration,
  allCategories: List[String])(implicit ec: ExecutionContext) extends OAuth2Service with LazyLogging {

  protected val introspectAccessToken: Boolean = configuration.jwt.exists(_.accessTokenIsJwt)
  protected val useIdToken: Boolean = configuration.jwt.exists(_.userinfoFromIdToken)

  protected val authorizationsCache = new DefaultAsyncCache[String, (LoggedUser, Deadline)](CacheConfig(new ExpiryConfig[String, (LoggedUser, Deadline)]() {
    override def expireAfterWriteFn(key: String, value: (LoggedUser, Deadline), now: Deadline): Option[Deadline] = Some(value._2)
  }))

  protected val jwtValidator: Option[JwtValidator] = configuration.jwt.map(_.authServerPublicKey).map(new JwtValidator(_))

  def authenticate(code: String): Future[OAuth2AuthenticateData] = {
    clientApi.accessTokenRequest(code).map { resp: DefaultAccessTokenResponse =>
      authorizationsCache.put(resp.access_token)(
        createExpiringLoggedUser(resp.access_token, resp.expires_in.map(millisecondsToDeadline), resp.id_token)
      )
      OAuth2AuthenticateData(
        access_token = resp.access_token,
        token_type = resp.token_type,
        refresh_token = resp.refresh_token
      )
    }
  }

  def authorize(token: String): Future[LoggedUser] =
    authorizationsCache.getOrCreate(token) {
      createExpiringLoggedUser(token, introspectAccessToken(token).flatMap(_.exp).map(millisecondsToDeadline), None)
    }.map { case (user, _) => user }

  protected def millisecondsToDeadline(expiration: Long): Deadline = Deadline(FiniteDuration(expiration, MILLISECONDS))

  protected def createExpiringLoggedUser(token: String, expiration: Option[Deadline], idToken: Option[String]): Future[(LoggedUser, Deadline)] =
    getProfile(token, idToken)
      .map(oAuth2Profile.getLoggedUser(_, configuration, allCategories))
      .map((_, expiration.getOrElse(Deadline.now + configuration.defaultTokenExpirationTime)))

  protected def introspectAccessToken(token: String): Option[DecodedJwtAccessToken] = {
    for {jwtValidator <- jwtValidator if introspectAccessToken}
      yield jwtValidator.introspect[DecodedJwtAccessToken](token)
      match {
        case Valid(claims) => claims
        case Invalid(jwtErrors) => throw OAuth2CompoundException(jwtErrors)
      }
  }

  /**
   * First checks whether a profile can be obtained from the ID token (provided authentication.jwt configured),
   * then tries to obtain the profile from a sent request.
   */
  def getProfile(token: String, idToken: Option[String]): Future[ProfileResponse] = {
    (for {jwtValidator <- jwtValidator; idToken <- idToken if useIdToken}
      yield jwtValidator.introspect[ProfileResponse](idToken))
    match {
      case Some(Valid(profile)) => Future(profile)
      case Some(Invalid(jwtErrors)) => clientApi.profileRequest(token) recover { case OAuth2CompoundException(requestErrors) =>
        throw OAuth2CompoundException(jwtErrors concatNel requestErrors)
      }
      case None => clientApi.profileRequest(token)
    }
  }
}
