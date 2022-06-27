package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultAsyncCache, ExpiryConfig}

import scala.concurrent.duration.Deadline
import scala.concurrent.{ExecutionContext, Future}

class CachingOAuth2Service[
  UserInfoData,
  AuthorizationData <: OAuth2AuthorizationData
](delegate: OAuth2Service[UserInfoData, AuthorizationData],
  configuration: OAuth2Configuration)
 (implicit ec: ExecutionContext) extends OAuth2Service[UserInfoData, AuthorizationData] with LazyLogging {

  protected val authorizationsCache = new DefaultAsyncCache[String, (UserInfoData, Deadline)](CacheConfig(new ExpiryConfig[String, (UserInfoData, Deadline)]() {
    override def expireAfterWriteFn(key: String, value: (UserInfoData, Deadline), now: Deadline): Option[Deadline] = Some(value._2)
  }))

  def obtainAuthorizationAndUserInfo(authorizationCode: String, redirectUri: String): Future[(AuthorizationData, UserInfoData)] = {
    delegate.obtainAuthorizationAndUserInfo(authorizationCode, redirectUri).map { case (authorization, userInfo) =>
      authorizationsCache.put(authorization.accessToken) {
        val deadline = Deadline.now + authorization.expirationPeriod.getOrElse(defaultExpiration)
        logger.info(s"obtainAuthorizationAndUserInfo: ${authorization.expirationPeriod} $deadline")
        Future((userInfo, deadline))
      }
      (authorization, userInfo)
    }
  }

  def checkAuthorizationAndObtainUserinfo(accessToken: String): Future[(UserInfoData, Option[Deadline])] = {
    authorizationsCache.getOrCreate(accessToken) {
      delegate.checkAuthorizationAndObtainUserinfo(accessToken).map {
        case (userInfo, expiration) =>
          val deadline = expiration.getOrElse(Deadline.now + defaultExpiration)
          logger.info(s"checkAuthorizationAndObtainUserinfo: $deadline $defaultExpiration")
          (userInfo, deadline)
      }
    }.map { case (userInfo, expiration) => (userInfo, Some(expiration)) }
  }

  private lazy val defaultExpiration = configuration.defaultTokenExpirationTime
}
