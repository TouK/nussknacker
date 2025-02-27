package pl.touk.nussknacker.ui.security.oauth2

import com.github.benmanes.caffeine.cache.Ticker
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultCache, ExpiryConfig}

import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.util.{Failure, Success, Try}

class CachingOAuth2Service[
    UserInfoData,
    AuthorizationData <: OAuth2AuthorizationData
](
    delegate: OAuth2Service[UserInfoData, AuthorizationData],
    configuration: OAuth2Configuration,
    ticker: Ticker = Ticker.systemTicker()
)(implicit ec: ExecutionContext)
    extends OAuth2Service[UserInfoData, AuthorizationData]
    with LazyLogging {

  protected val authorizationsCache = new DefaultCache[String, (UserInfoData, Instant)](
    CacheConfig(new ExpiryConfig[String, (UserInfoData, Instant)]() {
      override def expireAfterWriteFn(key: String, value: (UserInfoData, Instant), now: Deadline): Option[Deadline] =
        Some(Deadline.now + FiniteDuration(Duration.between(Instant.now(), value._2).toNanos, TimeUnit.NANOSECONDS))
    }),
    ticker = ticker
  )

  def obtainAuthorizationAndAuthenticateUser(
      authorizationCode: String,
      redirectUri: String
  ): Future[(AuthorizationData, UserInfoData)] = {
    delegate.obtainAuthorizationAndAuthenticateUser(authorizationCode, redirectUri).map {
      case (authorization, userInfo) =>
        authorizationsCache.put(authorization.accessToken) {
          val expirationDuration = authorization.expirationPeriod.getOrElse(defaultExpirationDuration)
          (userInfo, Instant.now() plusNanos expirationDuration.toNanos)
        }
        (authorization, userInfo)
    }
  }

  override def checkAuthorizationAndAuthenticateUser(accessToken: String): Future[(UserInfoData, Option[Instant])] = {
    val userInfo = authorizationsCache.get(accessToken) match {
      case Some(value) =>
        Future.successful(value)
      case None =>
        delegate.checkAuthorizationAndAuthenticateUser(accessToken).map { case (userInfo, expirationInstant) =>
          val expiration = expirationInstant.getOrElse(Instant.now() plusNanos defaultExpirationDuration.toNanos)
          val value      = (userInfo, expiration)
          Try(authorizationsCache.put(accessToken)(value)) match {
            case Failure(exception) => logger.warn("Failed to populate cache.", exception)
            case Success(_)         => ()
          }
          value
        }
    }
    userInfo.map { case (userInfo, expiration) => (userInfo, Some(expiration)) }
  }

  override private[oauth2] def introspectAccessToken(accessToken: String): Future[IntrospectedAccessTokenData] =
    delegate.introspectAccessToken(accessToken)

  override private[oauth2] def authenticateUser(
      accessToken: String,
      accessTokenData: IntrospectedAccessTokenData
  ): Future[UserInfoData] =
    delegate.authenticateUser(accessToken, accessTokenData)

  private lazy val defaultExpirationDuration = configuration.defaultTokenExpirationDuration
}
