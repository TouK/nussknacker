package pl.touk.nussknacker.ui.security.oauth2

import com.github.benmanes.caffeine.cache.Ticker
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultCache, ExpiryConfig}

import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class CachingOAuth2Service[
  UserInfoData,
  AuthorizationData <: OAuth2AuthorizationData
](delegate: OAuth2Service[UserInfoData, AuthorizationData],
  configuration: OAuth2Configuration,
  ticker: Ticker = Ticker.systemTicker())
 (implicit ec: ExecutionContext) extends OAuth2Service[UserInfoData, AuthorizationData] with LazyLogging {

  protected val authorizationsCache = new DefaultCache[String, (UserInfoData, Instant)](CacheConfig(new ExpiryConfig[String, (UserInfoData, Instant)]() {
    override def expireAfterWriteFn(key: String, value: (UserInfoData, Instant), now: Deadline): Option[Deadline] =
      Some(Deadline.now + FiniteDuration(Duration.between(Instant.now(), value._2).toNanos, TimeUnit.NANOSECONDS))
  }), ticker = ticker)

  def obtainAuthorizationAndUserInfo(authorizationCode: String, redirectUri: String): Future[(AuthorizationData, UserInfoData)] = {
    delegate.obtainAuthorizationAndUserInfo(authorizationCode, redirectUri).map { case (authorization, userInfo) =>
      authorizationsCache.put(authorization.accessToken) {
        val expirationDuration = authorization.expirationPeriod.getOrElse(defaultExpirationDuration)
        (userInfo, Instant.now() plusNanos expirationDuration.toNanos)
      }
      (authorization, userInfo)
    }
  }

  override def checkAuthorizationAndObtainUserinfo(accessToken: String): Future[(UserInfoData, Option[Instant])] = {
    val userInfo = authorizationsCache.get(accessToken) match {
      case Some(value) =>
        Future.successful(value)
      case None =>
        delegate.checkAuthorizationAndObtainUserinfo(accessToken).map {
          case (userInfo, expirationInstant) =>
            val expiration = expirationInstant.getOrElse(Instant.now() plusNanos defaultExpirationDuration.toNanos)
            val value = (userInfo, expiration)
            Try(authorizationsCache.put(accessToken)(value)) match {
              case Failure(exception) => logger.warn("Failed to populate cache.", exception)
              case Success(_) => ()
            }
            value
        }
    }
    userInfo.map { case (userInfo, expiration) => (userInfo, Some(expiration)) }
  }

  override def introspectAccessToken(accessToken: String): Future[AccessTokenData] =
    throw new IllegalAccessError("CachingOAuth2Service.introspectAccessToken shouldn't be used directly - checkAuthorizationAndObtainUserinfo should be called instead")

  override def obtainUserInfo(accessToken: String, accessTokenData: AccessTokenData): Future[UserInfoData] =
    throw new IllegalAccessError("CachingOAuth2Service.obtainUserInfo shouldn't be used directly - checkAuthorizationAndObtainUserinfo should be called instead")

  private lazy val defaultExpirationDuration = configuration.defaultTokenExpirationDuration
}
