package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultAsyncCache, ExpiryConfig}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.duration.{Deadline, FiniteDuration, MILLISECONDS}
import scala.concurrent.{ExecutionContext, Future}

class BaseOAuth2Service[
  ProfileResponse: Decoder,
  AccessTokenResponse <: StandardAccessTokenResponse : Decoder
](clientApi: OAuth2ClientApi[ProfileResponse, AccessTokenResponse],
  createLoggedUser: (ProfileResponse) => LoggedUser,
  configuration: OAuth2Configuration)
 (implicit ec: ExecutionContext) extends OAuth2Service with LazyLogging {

  protected val authorizationsCache = new DefaultAsyncCache[String, (LoggedUser, Deadline)](CacheConfig(new ExpiryConfig[String, (LoggedUser, Deadline)]() {
    override def expireAfterWriteFn(key: String, value: (LoggedUser, Deadline), now: Deadline): Option[Deadline] = Some(value._2)
  }))

  import pl.touk.nussknacker.ui.security.oauth2.BaseOAuth2Service._

  def authenticate(code: String): Future[OAuth2AuthenticateData] = {
    clientApi.accessTokenRequest(code).map { resp: AccessTokenResponse =>
      authorizationsCache.put(resp.access_token) {
        getProfile(resp).map((createExpiringLoggedUser _).tupled)
      }
      OAuth2AuthenticateData(
        access_token = resp.access_token,
        token_type = resp.token_type,
        refresh_token = resp.refresh_token
      )
    }
  }

  def authorize(accessToken: String): Future[LoggedUser] =
    authorizationsCache.getOrCreate(accessToken) {
      getProfile(accessToken, None).map((createExpiringLoggedUser _).tupled)
    }.map { case (user, _) => user }

  protected def createExpiringLoggedUser(profile: ProfileResponse, expiration: Option[Deadline]): (LoggedUser, Deadline) =
    (createLoggedUser(profile), expiration.getOrElse(Deadline.now + configuration.defaultTokenExpirationTime))

  protected def getProfile(resp: AccessTokenResponse): Future[(ProfileResponse, Option[Deadline])] =
    getProfile(resp.access_token, resp.expires_in.map(millisToDeadline))

  protected def getProfile(accessToken: String, expiration: Option[Deadline]): Future[(ProfileResponse, Option[Deadline])] =
    clientApi.profileRequest(accessToken).map((_, expiration))
}

object BaseOAuth2Service {
  def millisToDeadline(exp: Long): Deadline =
    Deadline(FiniteDuration(exp, MILLISECONDS))
}