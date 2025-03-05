package pl.touk.nussknacker.ui.security.oauth2

import com.github.benmanes.caffeine.cache.Ticker
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import pl.touk.nussknacker.ui.security.oidc.{DefaultOidcAuthorizationData, OidcUserInfo}

import java.time.{Clock, Instant}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class CachingOAuth2ServiceSpec extends AnyFunSpec with ScalaFutures with Matchers with WithJwtOauth2Service {

  final override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  implicit val clock: Clock = Clock.systemUTC()

  private var currentTime = Deadline.now

  private var prev = Deadline.now

  private val ticker = new Ticker {

    override def read(): Long = {
      if (currentTime != prev) {
        prev = currentTime
      }
      currentTime.time.toNanos
    }

  }

  private var checkRecordings = Map[String, Int]()

  private val recordingJwtOauth2Service = new OAuth2Service[OidcUserInfo, DefaultOidcAuthorizationData] {

    override def obtainAuthorizationAndAuthenticateUser(
        authorizationCode: String,
        redirectUri: String
    ): Future[(DefaultOidcAuthorizationData, OidcUserInfo)] =
      jwtOAuth2Service.obtainAuthorizationAndAuthenticateUser(authorizationCode, redirectUri)

    override def checkAuthorizationAndAuthenticateUser(
        accessToken: String
    ): Future[(OidcUserInfo, Option[Instant])] = {
      checkRecordings = checkRecordings + (accessToken -> (checkRecordings.getOrElse(accessToken, 0) + 1))
      jwtOAuth2Service.checkAuthorizationAndAuthenticateUser(accessToken)
    }

    override private[oauth2] def introspectAccessToken(accessToken: String): Future[IntrospectedAccessTokenData] = ???

    override private[oauth2] def authenticateUser(
        accessToken: String,
        accessTokenData: IntrospectedAccessTokenData
    ): Future[OidcUserInfo] =
      ???

  }

  private val cachingOAuth2Service = new CachingOAuth2Service(recordingJwtOauth2Service, config, ticker)

  it("should cache token only for period when token is valid") {
    val token =
      JwtCirce.encode(JwtClaim().about("admin").to(audience).expiresIn(180), keyPair.getPrivate, JwtAlgorithm.RS256)
    checkRecordings.get(token) shouldBe None

    cachingOAuth2Service.checkAuthorizationAndAuthenticateUser(token).futureValue
    checkRecordings(token) shouldBe 1

    currentTime += 179.seconds
    cachingOAuth2Service.checkAuthorizationAndAuthenticateUser(token).futureValue
    checkRecordings(token) shouldBe 1

    currentTime += 2.seconds
    cachingOAuth2Service.checkAuthorizationAndAuthenticateUser(token).futureValue
    checkRecordings(token) shouldBe 2
  }

}
