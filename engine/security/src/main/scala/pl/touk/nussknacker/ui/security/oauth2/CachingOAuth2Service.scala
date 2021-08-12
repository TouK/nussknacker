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

  def obtainAuthorizationAndUserInfo(authorizationCode: String): Future[(AuthorizationData, UserInfoData)] = {
    delegate.obtainAuthorizationAndUserInfo(authorizationCode).map { case (authorization, userInfo) =>
      authorizationsCache.put(authorization.accessToken) {
        Future((userInfo, Deadline.now + authorization.expirationPeriod.getOrElse(defaultExpiration)))
      }
      (authorization, userInfo)
    }
  }

  def checkAuthorizationAndObtainUserinfo(accessToken: String): Future[(UserInfoData, Option[Deadline])] = {
    authorizationsCache.getOrCreate(accessToken) {
      delegate.checkAuthorizationAndObtainUserinfo(accessToken).map {
        case (userInfo, expiration) => (userInfo, expiration.getOrElse(Deadline.now + defaultExpiration))
      }
    }.map { case (userInfo, expiration) => (userInfo, Some(expiration)) }
  }

  private lazy val defaultExpiration = configuration.defaultTokenExpirationTime
}
